package dev.hata.scheduler.dealership.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipConfigPort;
import dev.hata.scheduler.common.provider.DealershipValidationProvider;
import dev.hata.scheduler.dealership.dto.DealershipResponse;
import dev.hata.scheduler.dealership.model.Dealership;
import dev.hata.scheduler.dealership.repository.DealershipBayRepository;
import dev.hata.scheduler.dealership.repository.DealershipRepository;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dealership feature domain service (design D4, SYSTEM_DESIGN §4.2.5).
 */
@Service
public class DealershipService implements DealershipValidationProvider, DealershipConfigPort {

    private final DealershipRepository dealerships;
    private final DealershipBayRepository dealershipBayRepository;

    public DealershipService(DealershipRepository dealerships, DealershipBayRepository dealershipBayRepository) {
        this.dealerships = dealerships;
        this.dealershipBayRepository = dealershipBayRepository;
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
     * Returns empty when the dealership is not found or is inactive; results are
     * cached for 15 minutes and never cache missing lookups (Qodo #11).
     */
    @Override
    @Transactional(readOnly = true)
    // Spring unwraps the Optional in #result for the unless check: present
    // configs evaluate non-null (cached), empty lookups evaluate null (never
    // cached, Qodo #11).
    @Cacheable(cacheNames = "dealership-config", key = "#dealershipId.toString()", unless = "#result == null")
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
        return dealershipBayRepository.existsByIdAndDealershipIdAndActiveTrue(bayId, dealershipId);
    }
}
