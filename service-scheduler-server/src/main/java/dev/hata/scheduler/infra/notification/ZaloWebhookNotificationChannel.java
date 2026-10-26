package dev.hata.scheduler.infra.notification;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Production HTTP webhook implementation of {@link NotificationChannel} for Zalo messaging.
 * Fails fast at startup if {@code app.auth.otp.webhook-url} is missing and never logs OTP payloads.
 */
@Component
@ConditionalOnProperty(name = "app.auth.otp.channel", havingValue = "zalo-webhook")
public class ZaloWebhookNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(ZaloWebhookNotificationChannel.class);

    private final RestClient restClient;
    private final URI webhookUri;

    public ZaloWebhookNotificationChannel(@Value("${app.auth.otp.webhook-url}") String webhookUrl) {
        this(RestClient.create(), webhookUrl);
    }

    public ZaloWebhookNotificationChannel(RestClient restClient, String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalStateException("app.auth.otp.webhook-url must be configured for zalo-webhook channel");
        }
        this.restClient = restClient;
        this.webhookUri = URI.create(webhookUrl.trim());
    }

    @Override
    public void send(String recipient, String message) {
        restClient
                .post()
                .uri(webhookUri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("recipient", recipient, "message", message))
                .retrieve()
                .toBodilessEntity();
        log.info("[ZaloWebhook] Delivered notification to {} (payload redacted)", recipient);
    }
}
