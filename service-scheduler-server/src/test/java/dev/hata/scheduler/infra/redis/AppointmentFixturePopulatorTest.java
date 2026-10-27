package dev.hata.scheduler.infra.redis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AppointmentFixturePopulatorTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private TransactionTemplate transactionTemplate;

    private AppointmentFixturePopulator populator;

    @BeforeEach
    void setUp() {
        populator = new AppointmentFixturePopulator(jdbc, transactionTemplate);
    }

    @Test
    void populateWrapsAllThreeUpsertsInOneTransaction() {
        LocalDate today = LocalDate.of(2026, 10, 27);

        doAnswer(invocation -> {
                    Consumer<TransactionStatus> action = invocation.getArgument(0);
                    action.accept(mock(TransactionStatus.class));
                    return null;
                })
                .when(transactionTemplate)
                .executeWithoutResult(any());

        populator.populate(today);

        verify(transactionTemplate, times(1)).executeWithoutResult(any());
        verify(jdbc, times(3)).update(anyString(), any(Object[].class));
    }

    @Test
    void populatePropagatesExceptionWhenExclusionConstraintFails() {
        LocalDate today = LocalDate.of(2026, 10, 27);

        doAnswer(invocation -> {
                    Consumer<TransactionStatus> action = invocation.getArgument(0);
                    action.accept(mock(TransactionStatus.class));
                    return null;
                })
                .when(transactionTemplate)
                .executeWithoutResult(any());

        doAnswer(invocation -> {
                    throw new DataIntegrityViolationException("exclusion constraint failed");
                })
                .when(jdbc)
                .update(anyString(), any(Object[].class));

        assertThatThrownBy(() -> populator.populate(today))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("exclusion constraint failed");
    }
}
