package dev.hata.scheduler.dealership.service;

import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.dealership.model.Dealership;
import dev.hata.scheduler.dealership.repository.DealershipRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dealership feature domain service (design D4, SYSTEM_DESIGN §4.2.5).
 */
@Service
public class DealershipService {

    private final DealershipRepository dealerships;

    public DealershipService(DealershipRepository dealerships) {
        this.dealerships = dealerships;
    }

    @Transactional(readOnly = true)
    public Optional<Dealership> findById(UUID dealershipId) {
        return dealerships.findById(dealershipId);
    }
}
