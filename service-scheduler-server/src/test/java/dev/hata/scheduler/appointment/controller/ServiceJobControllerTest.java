package dev.hata.scheduler.appointment.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.appointment.dto.AddJobNoteRequest;
import dev.hata.scheduler.appointment.dto.JobNoteResponse;
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

/**
 * Unit tests for the ServiceJobController notes endpoint.
 */
@ExtendWith(MockitoExtension.class)
class ServiceJobControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private AppointmentService appointmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ServiceJobController controller = new ServiceJobController(appointmentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void appendJobNoteSucceedsForAssignedTechnician() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        String note = "Customer asked for tire check";
        AddJobNoteRequest req = new AddJobNoteRequest(note);

        AuthPrincipal principal = new AuthPrincipal(
                technicianId,
                "TECHNICIAN",
                "+84900000003",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "jti");

        when(appointmentService.appendJobNote(jobId, technicianId, note, false, null))
                .thenReturn(new AppointmentService.JobNoteResult(jobId, note));
        when(appointmentService.readJobNotes(jobId)).thenReturn(new JobNoteResponse(jobId, List.of(note)));

        mockMvc.perform(post("/api/v1/jobs/{id}/notes", jobId)
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.notes[0]").value(note));
    }

    @Test
    void appendJobNoteSucceedsForManager() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        String note = "Manager updated scope";

        AuthPrincipal principal = new AuthPrincipal(
                managerId, "MANAGER", "+84900000002", UUID.fromString("00000000-0000-0000-0000-000000000001"), "jti");

        when(appointmentService.appendJobNote(
                        jobId, managerId, note, true, UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .thenReturn(new AppointmentService.JobNoteResult(jobId, note));
        when(appointmentService.readJobNotes(jobId)).thenReturn(new JobNoteResponse(jobId, List.of(note)));

        AddJobNoteRequest req = new AddJobNoteRequest(note);

        mockMvc.perform(post("/api/v1/jobs/{id}/notes", jobId)
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.notes[0]").value(note));
    }

    @Test
    void appendJobNoteRejectsCustomer() throws Exception {
        UUID jobId = UUID.randomUUID();

        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "CUSTOMER", "+84900000004", null, "jti");

        AddJobNoteRequest req = new AddJobNoteRequest("x");

        mockMvc.perform(post("/api/v1/jobs/{id}/notes", jobId)
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void appendJobNoteRejectsUnauthenticated() throws Exception {
        UUID jobId = UUID.randomUUID();
        AddJobNoteRequest req = new AddJobNoteRequest("x");

        mockMvc.perform(post("/api/v1/jobs/{id}/notes", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void appendJobNoteRejectsBlankNote() throws Exception {
        UUID jobId = UUID.randomUUID();

        AuthPrincipal principal = new AuthPrincipal(
                UUID.randomUUID(),
                "TECHNICIAN",
                "+84900000003",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "jti");

        AddJobNoteRequest req = new AddJobNoteRequest("");

        mockMvc.perform(post("/api/v1/jobs/{id}/notes", jobId)
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
