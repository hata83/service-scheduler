package dev.hata.scheduler.notification.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.filter.AuthFilter;
import dev.hata.scheduler.common.security.AuthPrincipal;
import dev.hata.scheduler.notification.dto.CommunicationLogResponse;
import dev.hata.scheduler.notification.service.NotificationService;
import dev.hata.scheduler.notification.service.SseEmitterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        NotificationController controller = new NotificationController(notificationService, sseEmitterRegistry);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void streamReturnsUnauthorizedWhenNoPrincipal() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/stream")).andExpect(status().isUnauthorized());
    }

    @Test
    void streamReturnsSseEmitterWhenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthPrincipal principal = new AuthPrincipal(userId, "CUSTOMER", "+84900000004", null, "jti-1");
        SseEmitter emitter = new SseEmitter(60_000L);
        when(sseEmitterRegistry.register("+84900000004")).thenReturn(emitter);

        mockMvc.perform(get("/api/v1/notifications/stream").requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isOk());

        verify(sseEmitterRegistry).register("+84900000004");
    }

    @Test
    void streamWithCloseAfterInitialReturnsStandaloneEmitterWithoutRegistering() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthPrincipal principal = new AuthPrincipal(userId, "CUSTOMER", "+84900000004", null, "jti-1");

        mockMvc.perform(get("/api/v1/notifications/stream")
                        .param("closeAfterInitial", "true")
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isOk());

        org.mockito.Mockito.verifyNoInteractions(sseEmitterRegistry);
    }

    @Test
    void myReturnsUnauthorizedWhenNoPrincipal() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/my")).andExpect(status().isUnauthorized());
    }

    @Test
    void myReturnsCustomerLogsWhenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthPrincipal principal = new AuthPrincipal(userId, "CUSTOMER", "+84900000004", null, "jti-1");
        CommunicationLogResponse logResp = new CommunicationLogResponse(
                UUID.randomUUID(), "SSE", "appointment.confirmed", "DELIVERED", Instant.now());
        when(notificationService.findCustomerLogs(userId)).thenReturn(List.of(logResp));

        mockMvc.perform(get("/api/v1/notifications/my")
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].channel").value("SSE"))
                .andExpect(jsonPath("$[0].eventType").value("appointment.confirmed"));

        verify(notificationService).findCustomerLogs(userId);
    }
}
