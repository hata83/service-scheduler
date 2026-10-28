package dev.hata.scheduler.dealership.repository;

import java.util.UUID;

import dev.hata.scheduler.dealership.model.DealershipBay;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for workshop bays in dealership domain.
 */
@Repository("dealershipBayRepository")
public interface DealershipBayRepository extends JpaRepository<DealershipBay, UUID> {

    boolean existsByIdAndDealershipIdAndActiveTrue(UUID id, UUID dealershipId);
}
