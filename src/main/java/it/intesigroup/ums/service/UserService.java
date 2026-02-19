package it.intesigroup.ums.service;

import it.intesigroup.ums.domain.Role;
import it.intesigroup.ums.domain.User;
import it.intesigroup.ums.domain.UserStatus;
import it.intesigroup.ums.dto.CreateUserRequest;
import it.intesigroup.ums.dto.UpdateUserRequest;
import it.intesigroup.ums.exception.ConflictException;
import it.intesigroup.ums.exception.NotFoundException;
import it.intesigroup.ums.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final AmqpTemplate amqpTemplate;
    private final String userExchange;
    private final String userCreatedRoutingKey;

    public UserService(UserRepository userRepository,
                       AmqpTemplate amqpTemplate,
                       @Value("${app.events.exchange}") String userExchange,
                       @Value("${app.events.routing.userCreated}") String userCreatedRoutingKey) {
        this.userRepository = userRepository;
        this.amqpTemplate = amqpTemplate;
        this.userExchange = userExchange;
        this.userCreatedRoutingKey = userCreatedRoutingKey;
    }

    @Transactional(readOnly = true)
    public Page<User> listUsers(Pageable pageable) {
        log.info("Recupero lista utenti");
        return userRepository.findAllActiveOrDisabled(pageable);
    }

    @Transactional(readOnly = true)
    public User getUser(UUID id) {
        log.info("Recupero utente {}", id);
        
        return userRepository.findById(id)
                .filter(u -> u.getStatus() != UserStatus.DELETED)
                .orElseThrow(() -> new NotFoundException("Utente non trovato"));
    }

    @Transactional
    public User createUser(CreateUserRequest req) {
        log.info("Creazione utente {}", req);

        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new ConflictException("Email già utilizzata");
        }
        if (userRepository.findByCodiceFiscale(req.getCodiceFiscale()).isPresent()) {
            throw new ConflictException("Codice fiscale già utilizzato");
        }

        User u = new User();
        Set<Role> roles = new HashSet<>(req.getRoles());

        u.setUsername(req.getUsername());
        u.setEmail(req.getEmail().toLowerCase());
        u.setCodiceFiscale(req.getCodiceFiscale().toUpperCase());
        u.setNome(req.getNome());
        u.setCognome(req.getCognome());
        
        u.setRoles(roles);
        u.setStatus(UserStatus.ACTIVE);

        // Persistenza dell'utente e assegnazione dell'identificativo
        User saved = userRepository.save(u);
        log.info("Utente {} creato con successo", saved);

        // Pubblicazione best-effort di un evento di dominio su RabbitMQ
        try {
            log.info("Pubblicazione evento userCreated per utente {}", saved.getId());
            amqpTemplate.convertAndSend(userExchange, userCreatedRoutingKey,
                    new UserCreatedEvent(saved.getId(), saved.getEmail(), saved.getRoles()));
        } catch (Exception e) {
            log.warn("Impossibile pubblicare evento userCreated per utente {}", saved.getId(), e);
        }

        return saved;
    }

    @Transactional
    public User updateUser(UUID id, UpdateUserRequest req) {
        log.info("Aggiornamento utente {}", id);

        User u = getUser(id);

        if (req.getCodiceFiscale() != null) {
            String cf = req.getCodiceFiscale().toUpperCase();
            if (!cf.equals(u.getCodiceFiscale()) &&
                userRepository.findByCodiceFiscale(cf).isPresent()) {
                throw new ConflictException("Codice fiscale già utilizzato");
            }
            u.setCodiceFiscale(cf);
        }

        if (req.getUsername() != null) u.setUsername(req.getUsername());
        if (req.getNome() != null) u.setNome(req.getNome());
        if (req.getCognome() != null) u.setCognome(req.getCognome());
        if (req.getRoles() != null) u.setRoles(new HashSet<>(req.getRoles()));

        User updated = userRepository.save(u);
        log.info("Utente {} aggiornato con successo", id);

        return updated;
    }

    @Transactional
    public void disableUser(UUID id) {
        log.info("Disabilitazione utente {}", id);

        User u = getUser(id);
        u.setStatus(UserStatus.DISABLED);
        
        userRepository.save(u);
        log.info("Utente {} disabilitato con successo", id);
    }

    @Transactional
    public void softDeleteUser(UUID id) {
        log.info("Cancellazione utente {}", id);

        // Soft delete: l'utente non viene rimosso fisicamente dal database
        User u = getUser(id);
        u.setStatus(UserStatus.DELETED);

        userRepository.save(u);
        log.info("Utente {} cancellato con successo", id);
    }

    public record UserCreatedEvent(UUID id, String email, Set<Role> roles) {}
}
