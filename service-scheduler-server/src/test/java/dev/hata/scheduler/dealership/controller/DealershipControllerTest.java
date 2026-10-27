package dev.hata.scheduler.dealership.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.exception.GlobalExceptionHandler;
import dev.hata.scheduler.dealership.dto.DealershipResponse;
import dev.hata.scheduler.dealership.service.DealershipService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DealershipControllerTest {

    @Mock
    private DealershipService dealershipService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DealershipController(dealershipService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listDealershipsReturnsActiveDealerships() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        DealershipResponse response = new DealershipResponse(
                id,
                "Hata Demo Service Center",
                "1 Le Loi",
                "Ho Chi Minh City",
                "Vietnam",
                "Asia/Ho_Chi_Minh",
                30,
                LocalTime.of(8, 0),
                LocalTime.of(17, 30));

        when(dealershipService.findAllActive()).thenReturn(List.of(response));

        mvc.perform(get("/api/v1/dealerships"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("Hata Demo Service Center"))
                .andExpect(jsonPath("$[0].slotIntervalMinutes").value(30));
    }
}
