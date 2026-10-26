package dev.hata.scheduler.infra.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Console stub implementation of {@link NotificationChannel} for Zalo messaging.
 * Redacts the message body when {@code app.auth.otp.echo} is disabled in production.
 */
@Component
public class ZaloStubNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(ZaloStubNotificationChannel.class);

    private final boolean echoEnabled;

    public ZaloStubNotificationChannel(@Value("${app.auth.otp.echo:true}") boolean echoEnabled) {
        this.echoEnabled = echoEnabled;
    }

    public ZaloStubNotificationChannel() {
        this(true);
    }

    @Override
    public void send(String recipient, String message) {
        if (echoEnabled) {
            log.info("[ZaloStub] Dispatching to {}: {}", recipient, message);
        } else {
            log.info("[ZaloStub] Dispatched notification to {} (payload redacted)", recipient);
        }
    }
}
