package dev.hata.scheduler.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipValidationProvider;
import dev.hata.scheduler.scheduling.dto.ServiceTypeResponse;
import dev.hata.scheduler.scheduling.model.ServiceType;
import dev.hata.scheduler.scheduling.repository.ServiceTypeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ServiceTypeServiceTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SERVICE_TYPE_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

    @Mock
    private ServiceTypeRepository repository;

    @Mock
    private DealershipValidationProvider dealershipValidation;

    private ServiceTypeService service;

    @BeforeEach
    void setUp() {
        service = new ServiceTypeService(repository, dealershipValidation);
    }

    @Test
    void findByDealershipIdReturnsActiveServiceTypes() {
        ServiceType st = new ServiceType();
        st.setId(SERVICE_TYPE_ID);
        st.setDealershipId(DEALERSHIP_ID);
        st.setCode("OIL_CHANGE");
        st.setName("Express Oil Change");
        st.setDescription("Quick engine oil change");
        st.setEstimatedMinutes(30);
        st.setActive(true);

        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(repository.findByDealershipIdAndActiveTrue(DEALERSHIP_ID)).thenReturn(List.of(st));

        List<ServiceTypeResponse> result = service.findByDealershipId(DEALERSHIP_ID);

        assertThat(result).hasSize(1);
        ServiceTypeResponse first = result.get(0);
        assertThat(first.id()).isEqualTo(SERVICE_TYPE_ID);
        assertThat(first.dealershipId()).isEqualTo(DEALERSHIP_ID);
        assertThat(first.code()).isEqualTo("OIL_CHANGE");
        assertThat(first.name()).isEqualTo("Express Oil Change");
        assertThat(first.description()).isEqualTo("Quick engine oil change");
        assertThat(first.estimatedMinutes()).isEqualTo(30);
    }

    @Test
    void findByDealershipIdThrowsNotFoundWhenDealershipMissing() {
        UUID unknownId = UUID.randomUUID();
        when(dealershipValidation.existsActiveDealership(unknownId)).thenReturn(false);

        assertThatThrownBy(() -> service.findByDealershipId(unknownId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }
}
