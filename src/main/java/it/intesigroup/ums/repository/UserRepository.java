package it.intesigroup.ums.repository;

import it.intesigroup.ums.domain.User;
import it.intesigroup.ums.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByCodiceFiscale(String codiceFiscale);

    @Query("select u from User u where u.status <> it.intesigroup.ums.domain.UserStatus.DELETED")
    Page<User> findAllActiveOrDisabled(Pageable pageable);
}
