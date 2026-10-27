package dev.hata.scheduler.common.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpStorage storage;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OtpProperties properties;
    private OtpService service;

    @BeforeEach
    void setUp() {
        properties = new OtpProperties(Duration.ofMinutes(5), true, "zalo-stub");
        service = new OtpService(storage, eventPublisher, properties);
    }

    @Test
    void generateAndSendPersistsCodeAndPublishesEvent() {
        String code = service.generateAndSend("+84900000004", OtpType.LOGIN);

        assertThat(code).matches("\\d{6}");
        verify(storage).save(eq("+84900000004"), eq(OtpType.LOGIN), eq(code), eq(Duration.ofMinutes(5)));

        ArgumentCaptor<OtpGeneratedEvent> captor = ArgumentCaptor.forClass(OtpGeneratedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        OtpGeneratedEvent event = captor.getValue();
        assertThat(event.phone()).isEqualTo("+84900000004");
        assertThat(event.code()).isEqualTo(code);
        assertThat(event.type()).isEqualTo(OtpType.LOGIN);
        assertThat(event.ttl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void verifyDelegatesToStorage() {
        when(storage.consume("+84900000004", OtpType.LOGIN, "123456")).thenReturn(true);
        when(storage.consume("+84900000004", OtpType.LOGIN, "000000")).thenReturn(false);

        assertThat(service.verify("+84900000004", OtpType.LOGIN, "123456")).isTrue();
        assertThat(service.verify("+84900000004", OtpType.LOGIN, "000000")).isFalse();
        assertThat(service.verify("+84900000004", OtpType.LOGIN, null)).isFalse();
        assertThat(service.verify("+84900000004", OtpType.LOGIN, "  ")).isFalse();
    }

    @Test
    void echoFlagReportsConfiguredState() {
        assertThat(service.isEchoEnabled()).isTrue();
    }
}
