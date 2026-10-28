package dev.hata.scheduler.notification.repository;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.notification.model.OutboxEvent;
import dev.hata.scheduler.notification.model.OutboxStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for outbox events.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<OutboxEvent> findByAggregateIdAndStatus(UUID aggregateId, OutboxStatus status);
}
