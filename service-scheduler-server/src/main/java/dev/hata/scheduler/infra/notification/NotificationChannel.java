package dev.hata.scheduler.infra.notification;

/**
 * Port for dispatching outbound messages across communication channels
 * (e.g. Zalo, SMS, Email, WhatsApp).
 */
@FunctionalInterface
public interface NotificationChannel {

    /**
     * Sends a message to the given recipient identifier.
     */
    void send(String recipient, String message);
}
