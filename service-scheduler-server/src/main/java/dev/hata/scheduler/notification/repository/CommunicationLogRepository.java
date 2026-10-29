package dev.hata.scheduler.notification.repository;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.notification.model.CommunicationLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for communication audit logs.
 */
@Repository
public interface CommunicationLogRepository extends JpaRepository<CommunicationLog, UUID> {

    List<CommunicationLog> findByCustomerIdOrderByDispatchedAtDesc(UUID customerId);

    List<CommunicationLog> findByDealershipIdOrderByDispatchedAtDesc(UUID dealershipId);

    List<CommunicationLog> findAllByOrderByDispatchedAtDesc();

    List<CommunicationLog> findTop200ByDealershipIdAndRecipientAndChannelOrderByDispatchedAtDesc(
            UUID dealershipId, String recipient, String channel);

    List<CommunicationLog> findTop200ByRecipientAndChannelOrderByDispatchedAtDesc(String recipient, String channel);
}
