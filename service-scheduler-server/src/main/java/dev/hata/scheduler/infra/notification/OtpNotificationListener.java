package dev.hata.scheduler.infra.notification;

import dev.hata.scheduler.common.otp.OtpGeneratedEvent;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Event listener handling {@link OtpGeneratedEvent} to dispatch verification codes
 * through the configured notification channel.
 */
@Component
public class OtpNotificationListener {

    private final NotificationChannel channel;

    public OtpNotificationListener(NotificationChannel channel) {
        this.channel = channel;
    }

    @EventListener
    public void onOtpGenerated(OtpGeneratedEvent event) {
        String message = "Your verification code is " + event.code();
        channel.send(event.phone(), message);
    }
}
