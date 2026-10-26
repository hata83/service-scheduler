package dev.hata.scheduler.dealership;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Dealership domain entity (design D4, SYSTEM_DESIGN §4.2). Represents a
 * dealership location with its business hours, timezone, and dynamic
 * JSON feature configurations.
 */
@Entity
@Table(name = "dealerships")
public class Dealership {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "country")
    private String country;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "features", columnDefinition = "jsonb")
    private String features;

    @Column(name = "slot_interval_minutes", nullable = false)
    private int slotIntervalMinutes;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "lunch_start")
    private LocalTime lunchStart;

    @Column(name = "lunch_end")
    private LocalTime lunchEnd;

    @Column(name = "max_advance_booking_days", nullable = false)
    private int maxAdvanceBookingDays;

    @Column(name = "dealership_cache_ttl_seconds", nullable = false)
    private int dealershipCacheTtlSeconds;

    @Column(name = "slot_cache_ttl_seconds", nullable = false)
    private int slotCacheTtlSeconds;

    @Column(name = "auto_confirm_appointments", nullable = false)
    private boolean autoConfirmAppointments;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    protected Dealership() {}

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getFeatures() {
        return features;
    }

    public void setFeatures(String features) {
        this.features = features;
        this.updatedAt = Instant.now();
    }

    public int getSlotIntervalMinutes() {
        return slotIntervalMinutes;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public LocalTime getLunchStart() {
        return lunchStart;
    }

    public LocalTime getLunchEnd() {
        return lunchEnd;
    }

    public int getMaxAdvanceBookingDays() {
        return maxAdvanceBookingDays;
    }

    public int getDealershipCacheTtlSeconds() {
        return dealershipCacheTtlSeconds;
    }

    public int getSlotCacheTtlSeconds() {
        return slotCacheTtlSeconds;
    }

    public boolean isAutoConfirmAppointments() {
        return autoConfirmAppointments;
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

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
