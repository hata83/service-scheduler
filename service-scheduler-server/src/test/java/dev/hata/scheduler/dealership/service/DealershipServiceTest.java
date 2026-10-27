package dev.hata.scheduler.dealership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.dealership.dto.DealershipResponse;
import dev.hata.scheduler.dealership.model.Dealership;
import dev.hata.scheduler.dealership.repository.DealershipRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DealershipServiceTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private DealershipRepository repository;

    private DealershipService service;

    @BeforeEach
    void setUp() {
        service = new DealershipService(repository);
    }

    @Test
    void findByIdReturnsDealershipAndUpdatesFeatures() {
        Dealership dealership = new Dealership();
        dealership.setFeatures("{\"autoConfirm\":false}");
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.of(dealership));

        Optional<Dealership> found = service.findById(DEALERSHIP_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getFeatures()).isEqualTo("{\"autoConfirm\":false}");
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void findAllActiveReturnsActiveDealerships() {
        Dealership dealership = new Dealership();
        when(repository.findByActiveTrue()).thenReturn(List.of(dealership));

        List<DealershipResponse> result = service.findAllActive();

        assertThat(result).hasSize(1);
    }

    @Test
    void existsActiveDealershipReturnsTrueWhenActive() {
        Dealership inactiveDealership = mock(Dealership.class);
        when(inactiveDealership.isActive()).thenReturn(false);
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.of(inactiveDealership));

        assertThat(service.existsActiveDealership(DEALERSHIP_ID)).isFalse();

        Dealership activeDealership = mock(Dealership.class);
        when(activeDealership.isActive()).thenReturn(true);
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.of(activeDealership));

        assertThat(service.existsActiveDealership(DEALERSHIP_ID)).isTrue();
    }

    @Test
    void existsActiveDealershipReturnsFalseWhenMissing() {
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.empty());

        assertThat(service.existsActiveDealership(DEALERSHIP_ID)).isFalse();
    }
}
