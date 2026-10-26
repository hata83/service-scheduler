package dev.hata.scheduler.infra.notification;

import static org.mockito.Mockito.verify;

import java.time.Duration;

import dev.hata.scheduler.common.otp.OtpGeneratedEvent;
import dev.hata.scheduler.common.otp.OtpType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OtpNotificationListenerTest {

    @Mock
    private NotificationChannel channel;

    @Test
    void forwardsCodeToChannelOnEvent() {
        OtpNotificationListener listener = new OtpNotificationListener(channel);
        OtpGeneratedEvent event = new OtpGeneratedEvent("+84900000004", "123456", OtpType.LOGIN, Duration.ofMinutes(5));

        listener.onOtpGenerated(event);

        verify(channel).send("+84900000004", "Your verification code is 123456");
    }

    @Test
    void zaloStubLogsMessageWithoutException() {
        ZaloStubNotificationChannel stub = new ZaloStubNotificationChannel();
        stub.send("+84900000004", "Test message");
    }
}
