package dev.hata.scheduler.scheduling.service;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipValidationProvider;
import dev.hata.scheduler.scheduling.dto.ServiceTypeResponse;
import dev.hata.scheduler.scheduling.repository.ServiceTypeRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Domain service for managing service types catalog.
 */
@Service
public class ServiceTypeService {

    private final ServiceTypeRepository repository;
    private final DealershipValidationProvider dealershipValidation;

    public ServiceTypeService(ServiceTypeRepository repository, DealershipValidationProvider dealershipValidation) {
        this.repository = repository;
        this.dealershipValidation = dealershipValidation;
    }

    @Transactional(readOnly = true)
    public List<ServiceTypeResponse> findByDealershipId(UUID dealershipId) {
        if (!dealershipValidation.existsActiveDealership(dealershipId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealership not found: " + dealershipId);
        }
        return repository.findByDealershipIdAndActiveTrue(dealershipId).stream()
                .map(ServiceTypeResponse::from)
                .toList();
    }
}
