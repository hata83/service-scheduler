package dev.hata.scheduler.scheduling.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.filter.AuthFilter;
import dev.hata.scheduler.common.security.AuthPrincipal;
import dev.hata.scheduler.scheduling.dto.ScheduleBayColumn;
import dev.hata.scheduler.scheduling.dto.ScheduleDayResponse;
import dev.hata.scheduler.scheduling.dto.ScheduleHistoryItem;
import dev.hata.scheduler.scheduling.dto.ScheduleHistoryQuery;
import dev.hata.scheduler.scheduling.service.ScheduleService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for the manager schedule controller role scope enforcement.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleControllerTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDate DATE = LocalDate.of(2026, 10, 28);

    @Mock
    private ScheduleService scheduleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ScheduleController controller = new ScheduleController(scheduleService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void managerReadsDayScheduleOfOwnDealership() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "MANAGER", "+84900000002", DEALERSHIP_ID, "jti");

        when(scheduleService.findDaySchedule(eq(DEALERSHIP_ID), eq(DATE)))
                .thenReturn(new ScheduleDayResponse(DEALERSHIP_ID, DATE, List.of()));

        mockMvc.perform(get("/api/v1/schedule/day")
                        .param("date", DATE.toString())
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealershipId").value(DEALERSHIP_ID.toString()))
                .andExpect(jsonPath("$.date").value(DATE.toString()));
    }

    @Test
    void customerCannotReadDaySchedule() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "CUSTOMER", "+84900000004", null, "jti");

        mockMvc.perform(get("/api/v1/schedule/day")
                        .param("date", DATE.toString())
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCannotReadForeignDealershipSchedule() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "MANAGER", "+84900000002", DEALERSHIP_ID, "jti");

        mockMvc.perform(get("/api/v1/schedule/day")
                        .param("date", DATE.toString())
                        .param("dealershipId", OTHER_DEALERSHIP_ID.toString())
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSelectsDealershipForDaySchedule() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "ADMIN", "+84900000001", null, "jti");

        when(scheduleService.findDaySchedule(eq(OTHER_DEALERSHIP_ID), eq(DATE)))
                .thenReturn(new ScheduleDayResponse(OTHER_DEALERSHIP_ID, DATE, List.of()));

        mockMvc.perform(get("/api/v1/schedule/day")
                        .param("date", DATE.toString())
                        .param("dealershipId", OTHER_DEALERSHIP_ID.toString())
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealershipId").value(OTHER_DEALERSHIP_ID.toString()));
    }

    @Test
    void adminWithoutDealershipGetsBadRequestForDaySchedule() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "ADMIN", "+84900000001", null, "jti");

        mockMvc.perform(get("/api/v1/schedule/day")
                        .param("date", DATE.toString())
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/schedule/day").param("date", DATE.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managerReadsFilteredHistory() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "MANAGER", "+84900000002", DEALERSHIP_ID, "jti");

        when(scheduleService.findHistory(any()))
                .thenReturn(List.of(new ScheduleHistoryItem(
                        UUID.randomUUID(),
                        DEALERSHIP_ID,
                        UUID.randomUUID(),
                        "+84900000004",
                        "Demo Customer",
                        "1HGCR2F83HA000001",
                        "CANCELLED",
                        null,
                        null,
                        null,
                        null)));

        mockMvc.perform(get("/api/v1/schedule/history")
                        .param("status", "CANCELLED")
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].customerPhone").value("+84900000004"));
    }

    @Test
    void technicianCannotReadHistory() throws Exception {
        AuthPrincipal principal =
                new AuthPrincipal(UUID.randomUUID(), "TECHNICIAN", "+84900000003", DEALERSHIP_ID, "jti");

        mockMvc.perform(get("/api/v1/schedule/history").requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminReadsHistoryOfAllDealerships() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "ADMIN", "+84900000001", null, "jti");

        when(scheduleService.findHistory(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/schedule/history").requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isOk());
    }

    @Test
    void invalidDateIsRejectedWithBadRequest() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "MANAGER", "+84900000002", DEALERSHIP_ID, "jti");

        mockMvc.perform(get("/api/v1/schedule/day")
                        .param("date", "not-a-date")
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidHistoryInstantIsRejectedWithBadRequest() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "MANAGER", "+84900000002", DEALERSHIP_ID, "jti");

        mockMvc.perform(get("/api/v1/schedule/history")
                        .param("from", "yesterday")
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historyAcceptsDateOnlyFromFilter() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "MANAGER", "+84900000002", DEALERSHIP_ID, "jti");

        when(scheduleService.findHistory(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/schedule/history")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-28")
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isOk());

        ArgumentCaptor<ScheduleHistoryQuery> captor = ArgumentCaptor.forClass(ScheduleHistoryQuery.class);
        verify(scheduleService).findHistory(captor.capture());
        ScheduleHistoryQuery query = captor.getValue();
        assertThat(query.from()).isNotNull();
        assertThat(query.to()).isNotNull();
        assertThat(query.to()).isAfter(query.from());
    }

    @Test
    void dayScheduleReturnsBayColumnsWithJobs() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "MANAGER", "+84900000002", DEALERSHIP_ID, "jti");

        UUID bayId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        when(scheduleService.findDaySchedule(eq(DEALERSHIP_ID), eq(DATE)))
                .thenReturn(new ScheduleDayResponse(
                        DEALERSHIP_ID,
                        DATE,
                        List.of(new ScheduleBayColumn(bayId, "Bay 1", List.of("STANDARD"), true, List.of()))));

        mockMvc.perform(get("/api/v1/schedule/day")
                        .param("date", DATE.toString())
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bays[0].bayId").value(bayId.toString()))
                .andExpect(jsonPath("$.bays[0].bayName").value("Bay 1"))
                .andExpect(jsonPath("$.bays[0].jobs").isArray())
                .andExpect(jsonPath("$.bays[0].active").value(true));
        assertThat(appointmentId).isNotNull();
        assertThat(jobId).isNotNull();
    }
}
