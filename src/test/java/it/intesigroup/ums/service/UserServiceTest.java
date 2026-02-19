package it.intesigroup.ums.service;

import it.intesigroup.ums.domain.Role;
import it.intesigroup.ums.domain.User;
import it.intesigroup.ums.domain.UserStatus;
import it.intesigroup.ums.dto.CreateUserRequest;
import it.intesigroup.ums.dto.UpdateUserRequest;
import it.intesigroup.ums.exception.ConflictException;
import it.intesigroup.ums.exception.NotFoundException;
import it.intesigroup.ums.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AmqpTemplate amqpTemplate;

    @InjectMocks
    private UserService userService;

    private final String exchange = "ums.user.events";
    private final String routingKey = "user.created";

    @BeforeEach
    void init() {
        userService = new UserService(userRepository, amqpTemplate, exchange, routingKey);
    }

    @Test
    void listUsers_returnsPageFromRepository() {
        PageRequest pageable = PageRequest.of(0, 10);
        User u = new User();
        Page<User> page = new PageImpl<>(List.of(u), pageable, 1);
        given(userRepository.findAllActiveOrDisabled(pageable)).willReturn(page);

        Page<User> result = userService.listUsers(pageable);

        assertThat(result.getContent()).containsExactly(u);
        verify(userRepository).findAllActiveOrDisabled(pageable);
    }

    @Test
    void getUser_returnsUserWhenNotDeleted() {
        UUID id = UUID.randomUUID();
        User u = new User();
        u.setStatus(UserStatus.ACTIVE);
        given(userRepository.findById(id)).willReturn(Optional.of(u));

        User result = userService.getUser(id);

        assertThat(result).isSameAs(u);
        verify(userRepository).findById(id);
    }

    @Test
    void getUser_throwsWhenNotFoundOrDeleted() {
        UUID id = UUID.randomUUID();
        User deleted = new User();
        deleted.setStatus(UserStatus.DELETED);
        given(userRepository.findById(id)).willReturn(Optional.of(deleted));

        assertThatThrownBy(() -> userService.getUser(id))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createUser_persistsUserAndPublishesEvent() {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("mrossi");
        req.setEmail("M.ROSSI@example.com");
        req.setCodiceFiscale("rssmra80a01h501u");
        req.setNome("Mario");
        req.setCognome("Rossi");
        req.setRoles(Set.of(Role.DEVELOPER));

        given(userRepository.findByEmail("M.ROSSI@example.com")).willReturn(Optional.empty());
        given(userRepository.findByCodiceFiscale("rssmra80a01h501u")).willReturn(Optional.empty());

        User saved = new User();
        //saved.setId(UUID.randomUUID());
        saved.setEmail("m.rossi@example.com");
        saved.setRoles(Set.of(Role.DEVELOPER));
        given(userRepository.save(any(User.class))).willReturn(saved);

        User result = userService.createUser(req);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User toSave = userCaptor.getValue();
        assertThat(toSave.getEmail()).isEqualTo("m.rossi@example.com");
        assertThat(toSave.getCodiceFiscale()).isEqualTo("RSSMRA80A01H501U");
        assertThat(toSave.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(toSave.getRoles()).containsExactly(Role.DEVELOPER);

        assertThat(result).isSameAs(saved);
        verify(amqpTemplate).convertAndSend((String) eq(exchange), (String) eq(routingKey), (Object) any());
    }

    @Test
    void createUser_doesNotFailWhenEventPublishThrows() {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("mrossi");
        req.setEmail("m.rossi@example.com");
        req.setCodiceFiscale("RSSMRA80A01H501U");
        req.setNome("Mario");
        req.setCognome("Rossi");
        req.setRoles(Set.of(Role.DEVELOPER));

        given(userRepository.findByEmail("m.rossi@example.com")).willReturn(Optional.empty());
        given(userRepository.findByCodiceFiscale("RSSMRA80A01H501U")).willReturn(Optional.empty());

        User saved = new User();
        //saved.setId(UUID.randomUUID());
        saved.setEmail("m.rossi@example.com");
        saved.setRoles(Set.of(Role.DEVELOPER));
        given(userRepository.save(any(User.class))).willReturn(saved);
        doThrow(new RuntimeException("rabbit error"))
                .when(amqpTemplate).convertAndSend(anyString(), anyString(), (Object) any());

        User result = userService.createUser(req);

        assertThat(result).isSameAs(saved);
        verify(amqpTemplate).convertAndSend(anyString(), anyString(), (Object) any());
    }

    @Test
    void createUser_throwsWhenEmailAlreadyUsed() {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("m.rossi@example.com");
        req.setCodiceFiscale("RSSMRA80A01H501U");
        req.setRoles(Set.of(Role.DEVELOPER));

        given(userRepository.findByEmail("m.rossi@example.com"))
                .willReturn(Optional.of(new User()));

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createUser_throwsWhenCodiceFiscaleAlreadyUsed() {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("m.rossi@example.com");
        req.setCodiceFiscale("RSSMRA80A01H501U");
        req.setRoles(Set.of(Role.DEVELOPER));

        given(userRepository.findByEmail("m.rossi@example.com"))
                .willReturn(Optional.empty());
        given(userRepository.findByCodiceFiscale("RSSMRA80A01H501U"))
                .willReturn(Optional.of(new User()));

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateUser_updatesFieldsAndChecksCodiceFiscaleConflict() {
        UUID id = UUID.randomUUID();
        User existing = new User();
        existing.setCodiceFiscale("OLD");
        existing.setUsername("old");
        existing.setNome("Old");
        existing.setCognome("Name");
        existing.setRoles(Set.of(Role.OPERATOR));
        existing.setStatus(UserStatus.ACTIVE);

        given(userRepository.findById(id)).willReturn(Optional.of(existing));
        given(userRepository.findByCodiceFiscale("NEWCF")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest req = new UpdateUserRequest();
        req.setCodiceFiscale("newcf");
        req.setUsername("newuser");
        req.setNome("Mario");
        req.setCognome("Rossi");
        req.setRoles(Set.of(Role.DEVELOPER));

        User updated = userService.updateUser(id, req);

        assertThat(updated.getCodiceFiscale()).isEqualTo("NEWCF");
        assertThat(updated.getUsername()).isEqualTo("newuser");
        assertThat(updated.getNome()).isEqualTo("Mario");
        assertThat(updated.getCognome()).isEqualTo("Rossi");
        assertThat(updated.getRoles()).containsExactly(Role.DEVELOPER);
        verify(userRepository).save(existing);
    }

    @Test
    void updateUser_throwsWhenCodiceFiscaleInUseByAnotherUser() {
        UUID id = UUID.randomUUID();
        User existing = new User();
        existing.setCodiceFiscale("OLD");
        given(userRepository.findById(id)).willReturn(Optional.of(existing));

        given(userRepository.findByCodiceFiscale("NEWCF"))
                .willReturn(Optional.of(new User()));

        UpdateUserRequest req = new UpdateUserRequest();
        req.setCodiceFiscale("newcf");

        assertThatThrownBy(() -> userService.updateUser(id, req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void disableUser_setsStatusDisabled() {
        UUID id = UUID.randomUUID();
        User existing = new User();
        existing.setStatus(UserStatus.ACTIVE);
        given(userRepository.findById(id)).willReturn(Optional.of(existing));

        userService.disableUser(id);

        assertThat(existing.getStatus()).isEqualTo(UserStatus.DISABLED);
        verify(userRepository).save(existing);
    }

    @Test
    void softDeleteUser_setsStatusDeleted() {
        UUID id = UUID.randomUUID();
        User existing = new User();
        existing.setStatus(UserStatus.ACTIVE);
        given(userRepository.findById(id)).willReturn(Optional.of(existing));

        userService.softDeleteUser(id);

        assertThat(existing.getStatus()).isEqualTo(UserStatus.DELETED);
        verify(userRepository).save(existing);
    }
}

