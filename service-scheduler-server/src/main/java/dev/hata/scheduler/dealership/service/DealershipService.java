package dev.hata.scheduler.dealership.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipConfigPort;
import dev.hata.scheduler.common.provider.DealershipValidationProvider;
import dev.hata.scheduler.dealership.dto.DealershipResponse;
import dev.hata.scheduler.dealership.model.Dealership;
import dev.hata.scheduler.dealership.repository.DealershipRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dealership feature domain service (design D4, SYSTEM_DESIGN §4.2.5).
 */
@Service
public class DealershipService implements DealershipValidationProvider, DealershipConfigPort {

    private static final String BAY_EXISTS_SQL =
            """
            SELECT EXISTS (
                SELECT 1
                FROM workshop_bays
                WHERE id = ?
                  AND dealership_id = ?
                  AND is_active = true
            )
            """;

    private final DealershipRepository dealerships;
    private final JdbcTemplate jdbcTemplate;

    public DealershipService(DealershipRepository dealerships, JdbcTemplate jdbcTemplate) {
        this.dealerships = dealerships;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Optional<Dealership> findById(UUID dealershipId) {
        return dealerships.findById(dealershipId);
    }

    @Transactional(readOnly = true)
    public List<DealershipResponse> findAllActive() {
        return dealerships.findByActiveTrue().stream()
                .map(DealershipResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveDealership(UUID dealershipId) {
        return dealerships.findById(dealershipId).map(Dealership::isActive).orElse(false);
    }

    /**
     * Returns the scheduling configuration for a dealership.
     * Returns empty when the dealership is not found or is inactive.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<DealershipConfig> findConfig(UUID dealershipId) {
        return dealerships
                .findById(dealershipId)
                .filter(Dealership::isActive)
                .map(d -> new DealershipConfig(
                        d.getId(),
                        d.getTimezone(),
                        d.getSlotIntervalMinutes(),
                        d.getOpenTime(),
                        d.getCloseTime(),
                        d.getLunchStart(),
                        d.getLunchEnd(),
                        d.getMaxAdvanceBookingDays(),
                        d.getSlotCacheTtlSeconds()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAutoConfirmEnabled(UUID dealershipId) {
        return dealerships
                .findById(dealershipId)
                .map(Dealership::isAutoConfirmAppointments)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public String getDealershipTimezone(UUID dealershipId) {
        return dealerships.findById(dealershipId).map(Dealership::getTimezone).orElse("Asia/Ho_Chi_Minh");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveBay(UUID dealershipId, UUID bayId) {
        if (dealershipId == null || bayId == null) {
            return false;
        }
        Boolean exists = jdbcTemplate.queryForObject(BAY_EXISTS_SQL, Boolean.class, bayId, dealershipId);
        return Boolean.TRUE.equals(exists);
    }
}
