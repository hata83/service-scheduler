package dev.hata.scheduler.scheduling.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.exception.GlobalExceptionHandler;
import dev.hata.scheduler.scheduling.dto.CostItemResponse;
import dev.hata.scheduler.scheduling.dto.PricingEstimateResponse;
import dev.hata.scheduler.scheduling.service.PricingService;

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
class PricingControllerTest {

    @Mock
    private PricingService pricingService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PricingController(pricingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void calculateEstimateReturns200WithCalculatedPricing() throws Exception {
        UUID dealershipId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID categoryId = UUID.fromString("11111111-0000-0000-0000-000000000001");
        UUID serviceTypeId = UUID.fromString("22222222-0000-0000-0000-000000000001");

        PricingEstimateResponse response = new PricingEstimateResponse(
                dealershipId,
                categoryId,
                List.of(new CostItemResponse(serviceTypeId, "STANDARD_SERVICE", new BigDecimal("150.00"))),
                new BigDecimal("150.00"));

        when(pricingService.calculateEstimate(dealershipId, categoryId, List.of(serviceTypeId)))
                .thenReturn(response);

        mvc.perform(get("/api/v1/dealerships/" + dealershipId + "/pricing/estimate")
                        .param("categoryId", categoryId.toString())
                        .param("serviceTypeIds", serviceTypeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealershipId").value(dealershipId.toString()))
                .andExpect(jsonPath("$.vehicleCategoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.items[0].serviceCode").value("STANDARD_SERVICE"))
                .andExpect(jsonPath("$.totalCost").value(150.00));
    }

    @Test
    void calculateEstimateReturns400WhenMissingPricingMapping() throws Exception {
        UUID dealershipId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID categoryId = UUID.fromString("11111111-0000-0000-0000-000000000001");
        UUID serviceTypeId = UUID.fromString("22222222-0000-0000-0000-000000000001");

        when(pricingService.calculateEstimate(dealershipId, categoryId, List.of(serviceTypeId)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing pricing configuration"));

        mvc.perform(get("/api/v1/dealerships/" + dealershipId + "/pricing/estimate")
                        .param("categoryId", categoryId.toString())
                        .param("serviceTypeIds", serviceTypeId.toString()))
                .andExpect(status().isBadRequest());
    }
}
