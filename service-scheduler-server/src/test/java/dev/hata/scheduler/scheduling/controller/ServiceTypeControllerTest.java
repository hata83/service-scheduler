package dev.hata.scheduler.scheduling.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.exception.GlobalExceptionHandler;
import dev.hata.scheduler.scheduling.dto.ServiceTypeResponse;
import dev.hata.scheduler.scheduling.service.ServiceTypeService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ServiceTypeControllerTest {

    @Mock
    private ServiceTypeService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ServiceTypeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listServiceTypesReturns200WithActiveServiceTypes() throws Exception {
        UUID dealershipId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID serviceTypeId = UUID.fromString("22222222-0000-0000-0000-000000000001");
        ServiceTypeResponse response = new ServiceTypeResponse(
                serviceTypeId,
                dealershipId,
                "OIL_CHANGE",
                "Express Oil Change",
                "Quick engine oil change",
                30,
                List.of("QUICK_LUBE"));

        when(service.findByDealershipId(dealershipId)).thenReturn(List.of(response));

        mvc.perform(get("/api/v1/dealerships/" + dealershipId + "/service-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(serviceTypeId.toString()))
                .andExpect(jsonPath("$[0].code").value("OIL_CHANGE"))
                .andExpect(jsonPath("$[0].estimatedMinutes").value(30))
                .andExpect(jsonPath("$[0].allowedBayTypes[0]").value("QUICK_LUBE"));
    }

    @Test
    void listServiceTypesReturns404WhenDealershipNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(service.findByDealershipId(unknownId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealership not found"));

        mvc.perform(get("/api/v1/dealerships/" + unknownId + "/service-types")).andExpect(status().isNotFound());
    }
}
