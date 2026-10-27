package dev.hata.scheduler.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipValidationProvider;
import dev.hata.scheduler.scheduling.dto.PricingEstimateResponse;
import dev.hata.scheduler.scheduling.model.ServiceType;
import dev.hata.scheduler.scheduling.model.ServiceTypeCost;
import dev.hata.scheduler.scheduling.repository.ServiceTypeCostRepository;
import dev.hata.scheduler.scheduling.repository.ServiceTypeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID SERVICE_TYPE_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

    @Mock
    private ServiceTypeRepository serviceTypeRepository;

    @Mock
    private ServiceTypeCostRepository costRepository;

    @Mock
    private DealershipValidationProvider dealershipValidation;

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService(serviceTypeRepository, costRepository, dealershipValidation);
    }

    @Test
    void calculateEstimateReturnsItemizedAndTotalCost() {
        ServiceType st = new ServiceType();
        st.setId(SERVICE_TYPE_ID);
        st.setDealershipId(DEALERSHIP_ID);
        st.setCode("STANDARD_SERVICE");
        st.setActive(true);

        ServiceTypeCost cost = new ServiceTypeCost();
        cost.setServiceTypeId(SERVICE_TYPE_ID);
        cost.setVehicleCategoryId(CATEGORY_ID);
        cost.setCost(new BigDecimal("150.00"));

        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(serviceTypeRepository.findByIdInAndActiveTrue(List.of(SERVICE_TYPE_ID)))
                .thenReturn(List.of(st));
        when(costRepository.findByServiceTypeIdAndVehicleCategoryId(SERVICE_TYPE_ID, CATEGORY_ID))
                .thenReturn(Optional.of(cost));

        PricingEstimateResponse estimate =
                pricingService.calculateEstimate(DEALERSHIP_ID, CATEGORY_ID, List.of(SERVICE_TYPE_ID));

        assertThat(estimate.dealershipId()).isEqualTo(DEALERSHIP_ID);
        assertThat(estimate.vehicleCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(estimate.items()).hasSize(1);
        assertThat(estimate.items().get(0).serviceTypeId()).isEqualTo(SERVICE_TYPE_ID);
        assertThat(estimate.items().get(0).serviceCode()).isEqualTo("STANDARD_SERVICE");
        assertThat(estimate.items().get(0).cost()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(estimate.totalCost()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void calculateEstimateThrowsBadRequestWhenCostMappingMissing() {
        ServiceType st = new ServiceType();
        st.setId(SERVICE_TYPE_ID);
        st.setDealershipId(DEALERSHIP_ID);
        st.setCode("STANDARD_SERVICE");
        st.setActive(true);

        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(serviceTypeRepository.findByIdInAndActiveTrue(List.of(SERVICE_TYPE_ID)))
                .thenReturn(List.of(st));
        when(costRepository.findByServiceTypeIdAndVehicleCategoryId(SERVICE_TYPE_ID, CATEGORY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> pricingService.calculateEstimate(DEALERSHIP_ID, CATEGORY_ID, List.of(SERVICE_TYPE_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void calculateEstimateThrowsNotFoundWhenDealershipMissing() {
        UUID unknownId = UUID.randomUUID();
        when(dealershipValidation.existsActiveDealership(unknownId)).thenReturn(false);

        assertThatThrownBy(() -> pricingService.calculateEstimate(unknownId, CATEGORY_ID, List.of(SERVICE_TYPE_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void calculateEstimateThrowsBadRequestWhenServiceTypeIdsEmptyOrNull() {
        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);

        assertThatThrownBy(() -> pricingService.calculateEstimate(DEALERSHIP_ID, CATEGORY_ID, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");

        assertThatThrownBy(() -> pricingService.calculateEstimate(DEALERSHIP_ID, CATEGORY_ID, List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void calculateEstimateThrowsBadRequestWhenServiceTypeNotFoundOrDifferentDealership() {
        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(serviceTypeRepository.findByIdInAndActiveTrue(List.of(SERVICE_TYPE_ID)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> pricingService.calculateEstimate(DEALERSHIP_ID, CATEGORY_ID, List.of(SERVICE_TYPE_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");

        ServiceType foreign = new ServiceType();
        foreign.setId(SERVICE_TYPE_ID);
        foreign.setDealershipId(UUID.randomUUID());
        when(serviceTypeRepository.findByIdInAndActiveTrue(List.of(SERVICE_TYPE_ID)))
                .thenReturn(List.of(foreign));

        assertThatThrownBy(() -> pricingService.calculateEstimate(DEALERSHIP_ID, CATEGORY_ID, List.of(SERVICE_TYPE_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }
}
