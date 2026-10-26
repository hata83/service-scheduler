package dev.hata.scheduler.customer.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The {@code customers} profile row. A customer owns one profile that carries
 * the first and last names (design D4).
 */
@Entity
@Table(name = "customers")
public class CustomerProfile {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "dealership_id")
    private UUID dealershipId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "locale")
    private String locale;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CustomerProfile() {}

    /**
     * Fills the row for a new customer profile.
     */
    public void assign(UUID userId, UUID dealershipId, String firstName, String lastName) {
        this.userId = userId;
        this.dealershipId = dealershipId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.locale = "en_US";
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Changes the profile names.
     */
    public void setNames(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getDealershipId() {
        return dealershipId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getLocale() {
        return locale;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
