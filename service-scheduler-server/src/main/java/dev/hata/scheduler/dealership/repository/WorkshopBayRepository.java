package dev.hata.scheduler.dealership.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.dealership.model.WorkshopBay;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for workshop bays.
 */
@Repository
public interface WorkshopBayRepository extends JpaRepository<WorkshopBay, UUID> {

    List<WorkshopBay> findByDealershipIdAndActiveTrue(UUID dealershipId);

    Optional<WorkshopBay> findByIdAndDealershipIdAndActiveTrue(UUID id, UUID dealershipId);
}
