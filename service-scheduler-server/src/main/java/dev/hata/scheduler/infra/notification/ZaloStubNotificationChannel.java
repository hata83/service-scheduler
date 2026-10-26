package dev.hata.scheduler.infra.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Console stub implementation of {@link NotificationChannel} for Zalo messaging.
 */
@Component
public class ZaloStubNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(ZaloStubNotificationChannel.class);

    @Override
    public void send(String recipient, String message) {
        log.info("[ZaloStub] Dispatching to {}: {}", recipient, message);
    }
}
