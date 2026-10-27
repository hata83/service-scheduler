package dev.hata.scheduler.auth.repository;

import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.auth.model.User;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes the {@code users} table.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);
}
