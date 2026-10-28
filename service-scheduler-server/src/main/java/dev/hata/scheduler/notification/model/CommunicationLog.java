package dev.hata.scheduler.notification.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Audit log entity for multi-channel communication dispatches (SYSTEM_DESIGN §4.2.15).
 */
@Entity
@Table(name = "communication_logs")
public class CommunicationLog {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "dealership_id", nullable = false)
    private UUID dealershipId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message_payload", columnDefinition = "jsonb", nullable = false)
    private String messagePayload;

    @Column(name = "delivery_status", nullable = false, length = 30)
    private String deliveryStatus;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "dispatched_at", nullable = false)
    private Instant dispatchedAt;

    public CommunicationLog() {}

    public CommunicationLog(
            UUID id,
            UUID dealershipId,
            UUID appointmentId,
            UUID customerId,
            String channel,
            String recipient,
            String eventType,
            String messagePayload,
            String deliveryStatus,
            String errorMessage,
            Instant dispatchedAt) {
        this.id = id;
        this.dealershipId = dealershipId;
        this.appointmentId = appointmentId;
        this.customerId = customerId;
        this.channel = channel;
        this.recipient = recipient;
        this.eventType = eventType;
        this.messagePayload = messagePayload;
        this.deliveryStatus = deliveryStatus;
        this.errorMessage = errorMessage;
        this.dispatchedAt = dispatchedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDealershipId() {
        return dealershipId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getEventType() {
        return eventType;
    }

    public String getMessagePayload() {
        return messagePayload;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }
}
