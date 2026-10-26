package dev.hata.scheduler.auth.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The global {@code users} row. The table carries one global identity with
 * {@link UserType} ({@code CUSTOMER} or {@code EMPLOYEE}); employee authorization
 * roles ({@code ADMIN}, {@code MANAGER}, {@code TECHNICIAN}) live in
 * {@code employees.role} without duplication (design D4).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "email")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private UserType type;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User() {}

    /**
     * Fills the row for a new global user account.
     */
    public void assign(UUID id, String phone, UserType type) {
        this.id = Objects.requireNonNull(id);
        this.phone = Objects.requireNonNull(phone);
        this.type = Objects.requireNonNull(type);
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Changes the contact email. The phone stays the login identity.
     */
    public void setEmail(String email) {
        this.email = email;
        this.updatedAt = Instant.now();
    }

    /**
     * Activates or deactivates the account.
     */
    public void setActive(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public UserType getType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
