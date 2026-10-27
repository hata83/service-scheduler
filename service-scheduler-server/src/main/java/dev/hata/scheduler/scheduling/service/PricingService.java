package dev.hata.scheduler.scheduling.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.hata.scheduler.common.provider.DealershipValidationProvider;
import dev.hata.scheduler.scheduling.dto.CostItemResponse;
import dev.hata.scheduler.scheduling.dto.PricingEstimateResponse;
import dev.hata.scheduler.scheduling.model.ServiceType;
import dev.hata.scheduler.scheduling.model.ServiceTypeCost;
import dev.hata.scheduler.scheduling.repository.ServiceTypeCostRepository;
import dev.hata.scheduler.scheduling.repository.ServiceTypeRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Domain service for calculating instant service price estimates.
 */
@Service
public class PricingService {

    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceTypeCostRepository costRepository;
    private final DealershipValidationProvider dealershipValidation;

    public PricingService(
            ServiceTypeRepository serviceTypeRepository,
            ServiceTypeCostRepository costRepository,
            DealershipValidationProvider dealershipValidation) {
        this.serviceTypeRepository = serviceTypeRepository;
        this.costRepository = costRepository;
        this.dealershipValidation = dealershipValidation;
    }

    @Transactional(readOnly = true)
    public PricingEstimateResponse calculateEstimate(UUID dealershipId, UUID categoryId, List<UUID> serviceTypeIds) {
        if (!dealershipValidation.existsActiveDealership(dealershipId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealership not found: " + dealershipId);
        }
        if (serviceTypeIds == null || serviceTypeIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one service type must be specified");
        }

        List<ServiceType> serviceTypes = serviceTypeRepository.findByIdInAndActiveTrue(serviceTypeIds);
        Map<UUID, ServiceType> serviceTypeMap =
                serviceTypes.stream().collect(Collectors.toMap(ServiceType::getId, st -> st));

        List<CostItemResponse> items = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;

        for (UUID serviceTypeId : serviceTypeIds) {
            ServiceType serviceType = serviceTypeMap.get(serviceTypeId);
            if (serviceType == null || !serviceType.getDealershipId().equals(dealershipId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Service type not found or inactive: " + serviceTypeId);
            }

            ServiceTypeCost costMapping = costRepository
                    .findByServiceTypeIdAndVehicleCategoryId(serviceTypeId, categoryId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Missing pricing configuration for service "
                                    + serviceType.getCode()
                                    + " and vehicle category "
                                    + categoryId));

            items.add(new CostItemResponse(serviceTypeId, serviceType.getCode(), costMapping.getCost()));
            totalCost = totalCost.add(costMapping.getCost());
        }

        return new PricingEstimateResponse(dealershipId, categoryId, items, totalCost);
    }
}
