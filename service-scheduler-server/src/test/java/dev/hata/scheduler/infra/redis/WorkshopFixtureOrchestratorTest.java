package dev.hata.scheduler.infra.redis;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

@ExtendWith(MockitoExtension.class)
class WorkshopFixtureOrchestratorTest {

    @Mock
    private AppointmentFixturePopulator appointmentPopulator;

    @Mock
    private WorkshopSlotFixturePopulator slotPopulator;

    private WorkshopFixtureOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new WorkshopFixtureOrchestrator(appointmentPopulator, slotPopulator);
    }

    @Test
    void coordinatePassesSameDateToBothPopulators() {
        LocalDate today = LocalDate.of(2026, 10, 27);

        orchestrator.coordinate(today);

        verify(appointmentPopulator, times(1)).populate(today);
        verify(slotPopulator, times(1)).populate(today);
    }

    @Test
    void coordinateSwallowsSlotPopulatorExceptions() {
        LocalDate today = LocalDate.of(2026, 10, 27);

        doThrow(new RuntimeException("Redis connection refused"))
                .when(slotPopulator)
                .populate(today);

        orchestrator.coordinate(today);

        verify(appointmentPopulator, times(1)).populate(today);
        verify(slotPopulator, times(1)).populate(today);
    }

    @Test
    void runCoordinatesWithCurrentDate() {
        ApplicationArguments args = mock(ApplicationArguments.class);

        orchestrator.run(args);

        verify(appointmentPopulator, times(1)).populate(org.mockito.ArgumentMatchers.any(LocalDate.class));
        verify(slotPopulator, times(1)).populate(org.mockito.ArgumentMatchers.any(LocalDate.class));
    }
}
