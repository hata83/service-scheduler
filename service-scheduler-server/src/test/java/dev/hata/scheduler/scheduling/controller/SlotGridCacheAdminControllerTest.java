package dev.hata.scheduler.scheduling.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import dev.hata.scheduler.common.exception.GlobalExceptionHandler;
import dev.hata.scheduler.scheduling.service.SlotAvailabilityService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit test for {@link SlotGridCacheAdminController}. The controller is annotated
 * with {@code @Profile({"dev","test"})}; this test instantiates it directly via
 * MockMvc so the profile restriction is not exercised here.
 */
@ExtendWith(MockitoExtension.class)
class SlotGridCacheAdminControllerTest {

    @Mock
    private SlotAvailabilityService slotService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SlotGridCacheAdminController(slotService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void clearCachedSlotsEvictsByDealershipAndReturnsNoContent() throws Exception {
        UUID dealershipId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        mvc.perform(delete("/api/v1/slots/cache").param("dealershipId", dealershipId.toString()))
                .andExpect(status().isNoContent());

        verify(slotService).evictSlotsByDealership(dealershipId);
    }
}
