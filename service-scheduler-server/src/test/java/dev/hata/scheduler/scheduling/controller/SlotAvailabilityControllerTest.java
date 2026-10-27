package dev.hata.scheduler.scheduling.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.exception.GlobalExceptionHandler;
import dev.hata.scheduler.scheduling.dto.SlotAvailabilityResponse;
import dev.hata.scheduler.scheduling.service.SlotAvailabilityService;
import dev.hata.scheduler.scheduling.service.SlotDateOutOfRangeException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SlotAvailabilityControllerTest {

    @Mock
    private SlotAvailabilityService slotService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SlotAvailabilityController(slotService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAvailableSlotsReturns200WithSlots() throws Exception {
        UUID dealershipId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID serviceTypeId = UUID.fromString("22222222-0000-0000-0000-000000000002");
        LocalDate date = LocalDate.of(2026, 10, 28);

        OffsetDateTime start = OffsetDateTime.parse("2026-10-28T08:00:00+07:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-10-28T09:00:00+07:00");
        SlotAvailabilityResponse slot = new SlotAvailabilityResponse(start, end, false);

        when(slotService.getCachedAvailableSlots(dealershipId, serviceTypeId, date))
                .thenReturn(List.of(slot));

        mvc.perform(get("/api/v1/slots/available")
                        .param("dealershipId", dealershipId.toString())
                        .param("serviceTypeId", serviceTypeId.toString())
                        .param("date", "2026-10-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(false))
                .andExpect(jsonPath("$[0].startTime").value("2026-10-28T08:00:00+07:00"));
    }

    @Test
    void getAvailableSlotsReturns200WithEmptyListWhenNoneAvailable() throws Exception {
        UUID dealershipId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID serviceTypeId = UUID.fromString("22222222-0000-0000-0000-000000000002");
        LocalDate date = LocalDate.of(2026, 10, 28);

        when(slotService.getCachedAvailableSlots(dealershipId, serviceTypeId, date))
                .thenReturn(List.of());

        mvc.perform(get("/api/v1/slots/available")
                        .param("dealershipId", dealershipId.toString())
                        .param("serviceTypeId", serviceTypeId.toString())
                        .param("date", "2026-10-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getAvailableSlotsReturns400WhenDateExceedsMaxAdvanceBookingDays() throws Exception {
        UUID dealershipId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID serviceTypeId = UUID.fromString("22222222-0000-0000-0000-000000000002");
        LocalDate farFuture = LocalDate.of(2026, 11, 10);

        when(slotService.getCachedAvailableSlots(dealershipId, serviceTypeId, farFuture))
                .thenThrow(new SlotDateOutOfRangeException(
                        "Date 2026-11-10 exceeds the maximum advance booking window of 7 days from today."));

        mvc.perform(get("/api/v1/slots/available")
                        .param("dealershipId", dealershipId.toString())
                        .param("serviceTypeId", serviceTypeId.toString())
                        .param("date", "2026-11-10"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Slot date out of range"));
    }
}
