package dev.hata.scheduler.vehicle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipValidationProvider;
import dev.hata.scheduler.vehicle.dto.VehicleCategoryResponse;
import dev.hata.scheduler.vehicle.model.VehicleCategory;
import dev.hata.scheduler.vehicle.repository.VehicleCategoryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class VehicleCategoryServiceTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");

    @Mock
    private VehicleCategoryRepository repository;

    @Mock
    private DealershipValidationProvider dealershipValidation;

    private VehicleCategoryService service;

    @BeforeEach
    void setUp() {
        service = new VehicleCategoryService(repository, dealershipValidation);
    }

    @Test
    void findByDealershipIdReturnsActiveCategories() {
        VehicleCategory category = new VehicleCategory();
        category.setId(CATEGORY_ID);
        category.setDealershipId(DEALERSHIP_ID);
        category.setBrand("Toyota");
        category.setModel("Camry");
        category.setType("SEDAN");

        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(repository.findByDealershipId(DEALERSHIP_ID)).thenReturn(List.of(category));

        List<VehicleCategoryResponse> responses = service.findByDealershipId(DEALERSHIP_ID);

        assertThat(responses).hasSize(1);
        VehicleCategoryResponse first = responses.get(0);
        assertThat(first.id()).isEqualTo(CATEGORY_ID);
        assertThat(first.dealershipId()).isEqualTo(DEALERSHIP_ID);
        assertThat(first.brand()).isEqualTo("Toyota");
        assertThat(first.model()).isEqualTo("Camry");
        assertThat(first.type()).isEqualTo("SEDAN");
    }

    @Test
    void findByDealershipIdThrowsNotFoundWhenDealershipMissing() {
        UUID unknownId = UUID.randomUUID();
        when(dealershipValidation.existsActiveDealership(unknownId)).thenReturn(false);

        assertThatThrownBy(() -> service.findByDealershipId(unknownId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void existsCategoryInDealershipReturnsTrueWhenCategoryExists() {
        when(repository.existsByIdAndDealershipId(CATEGORY_ID, DEALERSHIP_ID)).thenReturn(true);

        assertThat(service.existsCategoryInDealership(DEALERSHIP_ID, CATEGORY_ID))
                .isTrue();
    }

    @Test
    void existsCategoryInDealershipReturnsFalseWhenNullOrMissing() {
        when(repository.existsByIdAndDealershipId(CATEGORY_ID, DEALERSHIP_ID)).thenReturn(false);

        assertThat(service.existsCategoryInDealership(DEALERSHIP_ID, CATEGORY_ID))
                .isFalse();
        assertThat(service.existsCategoryInDealership(null, CATEGORY_ID)).isFalse();
        assertThat(service.existsCategoryInDealership(DEALERSHIP_ID, null)).isFalse();
    }
}
