package dev.hata.scheduler.appointment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.appointment.dto.AppointmentResponse;
import dev.hata.scheduler.appointment.dto.CancelAppointmentResponse;
import dev.hata.scheduler.appointment.dto.CreateAppointmentRequest;
import dev.hata.scheduler.appointment.dto.ServiceJobItemRequest;
import dev.hata.scheduler.appointment.model.AppointmentStatus;
import dev.hata.scheduler.appointment.service.AppointmentService;
import dev.hata.scheduler.common.filter.AuthFilter;
import dev.hata.scheduler.common.security.AuthPrincipal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AppointmentControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private AppointmentService appointmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AppointmentController controller = new AppointmentController(appointmentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void createAppointmentReturns201WhenValid() throws Exception {
        UUID customerId = UUID.randomUUID();
        AuthPrincipal principal = new AuthPrincipal(customerId, "CUSTOMER", "+84900000004", null, "jti-1");
        UUID dealershipId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID serviceTypeId = UUID.randomUUID();
        UUID bayId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();

        CreateAppointmentRequest req = new CreateAppointmentRequest(
                dealershipId,
                categoryId,
                "1HGBH41JXMN109186",
                List.of(new ServiceJobItemRequest(
                        serviceTypeId, bayId, Instant.now(), Instant.now().plusSeconds(1800))));

        AppointmentResponse resp = new AppointmentResponse(
                appointmentId,
                dealershipId,
                customerId,
                categoryId,
                "1HGBH41JXMN109186",
                Instant.now(),
                Instant.now().plusSeconds(1800),
                AppointmentStatus.CONFIRMED,
                new BigDecimal("60.00"),
                null,
                List.of(),
                Instant.now());

        when(appointmentService.book(
                        any(CreateAppointmentRequest.class), eq(customerId), eq("+84900000004"), eq(idempotencyKey)))
                .thenReturn(resp);

        mockMvc.perform(post("/api/v1/appointments")
                        .header("X-Idempotency-Key", idempotencyKey.toString())
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void createAppointmentRejectsMissingIdempotencyKey() throws Exception {
        UUID customerId = UUID.randomUUID();
        AuthPrincipal principal = new AuthPrincipal(customerId, "CUSTOMER", "+84900000004", null, "jti-1");

        CreateAppointmentRequest req = new CreateAppointmentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "1HGBH41JXMN109186",
                List.of(new ServiceJobItemRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now(),
                        Instant.now().plusSeconds(1800))));

        mockMvc.perform(post("/api/v1/appointments")
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAppointmentRejectsUnauthenticated() throws Exception {
        CreateAppointmentRequest req = new CreateAppointmentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "1HGBH41JXMN109186",
                List.of(new ServiceJobItemRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now(),
                        Instant.now().plusSeconds(1800))));

        mockMvc.perform(post("/api/v1/appointments")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAppointmentsReturnsCustomerAppointments() throws Exception {
        UUID customerId = UUID.randomUUID();
        AuthPrincipal principal = new AuthPrincipal(customerId, "CUSTOMER", "+84900000004", null, "jti-1");
        UUID apptId = UUID.randomUUID();

        AppointmentResponse resp = new AppointmentResponse(
                apptId,
                UUID.randomUUID(),
                customerId,
                UUID.randomUUID(),
                "1HGBH41JXMN109186",
                Instant.now(),
                Instant.now().plusSeconds(1800),
                AppointmentStatus.CONFIRMED,
                new BigDecimal("60.00"),
                null,
                List.of(),
                Instant.now());

        when(appointmentService.findCustomerAppointments(customerId)).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/v1/appointments")
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(apptId.toString()));
    }

    @Test
    void getAppointmentByIdReturnsAppointmentDetails() throws Exception {
        UUID customerId = UUID.randomUUID();
        AuthPrincipal principal = new AuthPrincipal(customerId, "CUSTOMER", "+84900000004", null, "jti-1");
        UUID apptId = UUID.randomUUID();

        AppointmentResponse resp = new AppointmentResponse(
                apptId,
                UUID.randomUUID(),
                customerId,
                UUID.randomUUID(),
                "1HGBH41JXMN109186",
                Instant.now(),
                Instant.now().plusSeconds(1800),
                AppointmentStatus.CONFIRMED,
                new BigDecimal("60.00"),
                null,
                List.of(),
                Instant.now());

        when(appointmentService.findAppointmentById(apptId, customerId)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/appointments/" + apptId)
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(apptId.toString()));
    }

    @Test
    void cancelAppointmentReturnsCancelledResponse() throws Exception {
        UUID customerId = UUID.randomUUID();
        AuthPrincipal principal = new AuthPrincipal(customerId, "CUSTOMER", "+84900000004", null, "jti-1");
        UUID apptId = UUID.randomUUID();

        CancelAppointmentResponse cancelResp =
                new CancelAppointmentResponse(apptId, AppointmentStatus.CANCELLED, Instant.now());
        when(appointmentService.cancelAppointment(apptId, customerId, "+84900000004"))
                .thenReturn(cancelResp);

        mockMvc.perform(delete("/api/v1/appointments/" + apptId)
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(apptId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void endpointsRejectNonCustomerRoleWithForbidden() throws Exception {
        UUID staffId = UUID.randomUUID();
        AuthPrincipal staffPrincipal = new AuthPrincipal(staffId, "STAFF", "+84900000002", null, "jti-staff");

        CreateAppointmentRequest req = new CreateAppointmentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "1HGBH41JXMN109186",
                List.of(new ServiceJobItemRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now(),
                        Instant.now().plusSeconds(1800))));

        mockMvc.perform(post("/api/v1/appointments")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, staffPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/appointments").requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, staffPrincipal))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/appointments/" + UUID.randomUUID())
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, staffPrincipal))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/appointments/" + UUID.randomUUID())
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, staffPrincipal))
                .andExpect(status().isForbidden());
    }
}
