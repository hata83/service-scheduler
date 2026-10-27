package dev.hata.scheduler.vehicle.service;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipValidationProvider;
import dev.hata.scheduler.vehicle.dto.VehicleCategoryResponse;
import dev.hata.scheduler.vehicle.repository.VehicleCategoryRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Domain service for managing supported vehicle categories.
 */
@Service
public class VehicleCategoryService {

    private final VehicleCategoryRepository repository;
    private final DealershipValidationProvider dealershipValidation;

    public VehicleCategoryService(
            VehicleCategoryRepository repository, DealershipValidationProvider dealershipValidation) {
        this.repository = repository;
        this.dealershipValidation = dealershipValidation;
    }

    @Transactional(readOnly = true)
    public List<VehicleCategoryResponse> findByDealershipId(UUID dealershipId) {
        if (!dealershipValidation.existsActiveDealership(dealershipId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealership not found: " + dealershipId);
        }
        return repository.findByDealershipId(dealershipId).stream()
                .map(VehicleCategoryResponse::from)
                .toList();
    }
}
