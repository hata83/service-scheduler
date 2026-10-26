package dev.hata.scheduler.auth.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The {@code users} row. The table carries one identity for every role
 * (design D4). The entity stays in the {@code auth} package, so the auth
 * feature never imports another feature package (AGENTS constraint 1).
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

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "dealership_id")
    private UUID dealershipId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User() {}

    /**
     * Fills the row for a new account.
     */
    public void assign(UUID id, String phone, String role, UUID dealershipId) {
        this.id = id;
        this.phone = phone;
        this.role = role;
        this.dealershipId = dealershipId;
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

    public UUID getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public UUID getDealershipId() {
        return dealershipId;
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
