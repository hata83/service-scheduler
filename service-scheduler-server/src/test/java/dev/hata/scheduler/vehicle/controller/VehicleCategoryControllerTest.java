package dev.hata.scheduler.vehicle.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.exception.GlobalExceptionHandler;
import dev.hata.scheduler.vehicle.dto.VehicleCategoryResponse;
import dev.hata.scheduler.vehicle.service.VehicleCategoryService;

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
class VehicleCategoryControllerTest {

    @Mock
    private VehicleCategoryService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new VehicleCategoryController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listCategoriesReturns200WithCategories() throws Exception {
        UUID dealershipId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID categoryId = UUID.fromString("11111111-0000-0000-0000-000000000001");
        VehicleCategoryResponse response =
                new VehicleCategoryResponse(categoryId, dealershipId, "Toyota", "Camry", "SEDAN");

        when(service.findByDealershipId(dealershipId)).thenReturn(List.of(response));

        mvc.perform(get("/api/v1/dealerships/" + dealershipId + "/vehicle-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(categoryId.toString()))
                .andExpect(jsonPath("$[0].brand").value("Toyota"))
                .andExpect(jsonPath("$[0].model").value("Camry"))
                .andExpect(jsonPath("$[0].type").value("SEDAN"));
    }

    @Test
    void listCategoriesReturns404WhenDealershipNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(service.findByDealershipId(unknownId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealership not found"));

        mvc.perform(get("/api/v1/dealerships/" + unknownId + "/vehicle-categories"))
                .andExpect(status().isNotFound());
    }
}
