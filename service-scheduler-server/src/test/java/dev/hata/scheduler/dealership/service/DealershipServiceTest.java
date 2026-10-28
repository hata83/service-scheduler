package dev.hata.scheduler.dealership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipConfigPort.DealershipConfig;
import dev.hata.scheduler.dealership.dto.DealershipResponse;
import dev.hata.scheduler.dealership.model.Dealership;
import dev.hata.scheduler.dealership.model.WorkshopBay;
import dev.hata.scheduler.dealership.repository.DealershipRepository;
import dev.hata.scheduler.dealership.repository.WorkshopBayRepository;

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

    @Mock
    private WorkshopBayRepository workshopBayRepository;

    private DealershipService service;

    @BeforeEach
    void setUp() {
        service = new DealershipService(repository, workshopBayRepository);
    }

    @Test
    void findByIdReturnsDealershipAndUpdatesFeatures() {
        Dealership dealership = new Dealership();
        dealership.setFeatures("{\"autoConfirm\":false}");
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.of(dealership));

        Optional<Dealership> found = service.findById(DEALERSHIP_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getFeatures()).isEqualTo("{\"autoConfirm\":false}");
    }

    @Test
    void findAllActiveReturnsMappedResponses() {
        Dealership dealership = new Dealership();
        dealership.setName("Active Dealer");
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

    // --- findConfig ---

    @Test
    void findConfigReturnsPresentConfigForActiveDealership() {
        Dealership dealership = buildActiveDealership();
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.of(dealership));

        Optional<DealershipConfig> result = service.findConfig(DEALERSHIP_ID);

        assertThat(result).isPresent();
        DealershipConfig cfg = result.get();
        assertThat(cfg.id()).isEqualTo(DEALERSHIP_ID);
        assertThat(cfg.timezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(cfg.slotIntervalMinutes()).isEqualTo(30);
        assertThat(cfg.maxAdvanceBookingDays()).isEqualTo(7);
        assertThat(cfg.slotCacheTtlSeconds()).isEqualTo(3600);
        assertThat(cfg.openTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(cfg.closeTime()).isEqualTo(LocalTime.of(17, 30));
        assertThat(cfg.lunchStart()).isEqualTo(LocalTime.of(12, 0));
        assertThat(cfg.lunchEnd()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void findConfigReturnsEmptyWhenDealershipNotFound() {
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.empty());

        Optional<DealershipConfig> result = service.findConfig(DEALERSHIP_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    void findConfigReturnsEmptyWhenDealershipInactive() {
        Dealership inactive = buildActiveDealership();
        try {
            // Set is_active = false via reflection
            java.lang.reflect.Field f = inactive.getClass().getDeclaredField("active");
            f.setAccessible(true);
            f.set(inactive, false);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.of(inactive));

        Optional<DealershipConfig> result = service.findConfig(DEALERSHIP_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void isAutoConfirmEnabledChecksDealershipProperty() {
        Dealership dealership = mock(Dealership.class);
        when(dealership.isAutoConfirmAppointments()).thenReturn(true);
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.of(dealership));

        assertThat(service.isAutoConfirmEnabled(DEALERSHIP_ID)).isTrue();
    }

    @Test
    void getDealershipTimezoneReturnsConfiguredTimezone() {
        Dealership dealership = mock(Dealership.class);
        when(dealership.getTimezone()).thenReturn("Asia/Bangkok");
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.of(dealership));

        assertThat(service.getDealershipTimezone(DEALERSHIP_ID)).isEqualTo("Asia/Bangkok");
    }

    @Test
    void existsActiveBayChecksWorkshopBayRepository() {
        UUID bayId = UUID.randomUUID();
        when(workshopBayRepository.findByIdAndDealershipIdAndActiveTrue(bayId, DEALERSHIP_ID))
                .thenReturn(Optional.of(new WorkshopBay()));

        assertThat(service.existsActiveBay(DEALERSHIP_ID, bayId)).isTrue();
    }

    // --- builder ---

    private Dealership buildActiveDealership() {
        try {
            Dealership d = new Dealership();
            set(d, "id", DEALERSHIP_ID);
            set(d, "timezone", "Asia/Ho_Chi_Minh");
            set(d, "slotIntervalMinutes", 30);
            set(d, "openTime", LocalTime.of(8, 0));
            set(d, "closeTime", LocalTime.of(17, 30));
            set(d, "lunchStart", LocalTime.of(12, 0));
            set(d, "lunchEnd", LocalTime.of(13, 0));
            set(d, "maxAdvanceBookingDays", 7);
            set(d, "slotCacheTtlSeconds", 3600);
            set(d, "active", true);
            set(d, "name", "Demo Dealership");
            set(d, "createdAt", java.time.Instant.now());
            set(d, "updatedAt", java.time.Instant.now());
            set(d, "createdBy", UUID.randomUUID());
            set(d, "updatedBy", UUID.randomUUID());
            return d;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build test Dealership", ex);
        }
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private void set(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field f = findField(target.getClass(), fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException ex) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), name);
            }
            throw ex;
        }
    }
}
