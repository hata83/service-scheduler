package dev.hata.scheduler.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class TechnicianAvailabilityProviderTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TECH_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Mock
    private JdbcTemplate jdbc;

    private TechnicianAvailabilityProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TechnicianAvailabilityProvider(jdbc);
    }

    @Test
    void findAvailableTechnicianIdsReturnsListFromJdbc() {
        Instant start = Instant.parse("2026-10-28T01:00:00Z");
        Instant end = Instant.parse("2026-10-28T02:00:00Z");

        when(jdbc.queryForList(anyString(), eq(UUID.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of(TECH_ID));

        List<UUID> result = provider.findAvailableTechnicianIds(DEALERSHIP_ID, "BRAKES", "TECHNICIAN", start, end);

        assertThat(result).containsExactly(TECH_ID);
    }

    @Test
    void findAvailableTechnicianIdsReturnsEmptyWhenNoTechnicianAvailable() {
        Instant start = Instant.parse("2026-10-28T01:00:00Z");
        Instant end = Instant.parse("2026-10-28T02:00:00Z");

        when(jdbc.queryForList(anyString(), eq(UUID.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        List<UUID> result = provider.findAvailableTechnicianIds(DEALERSHIP_ID, "BRAKES", "TECHNICIAN", start, end);

        assertThat(result).isEmpty();
    }
}
