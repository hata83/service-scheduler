package dev.hata.scheduler.appointment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.hata.scheduler.appointment.model.JobStatus;
import dev.hata.scheduler.appointment.model.ServiceJob;

/**
 * Response DTO representing an itemized service job.
 */
public record ServiceJobResponse(
        UUID id,
        UUID serviceTypeId,
        UUID bayId,
        Instant startTime,
        Instant endTime,
        JobStatus status,
        int baseMinutes,
        BigDecimal estimateCost) {

    public static ServiceJobResponse from(ServiceJob job) {
        return new ServiceJobResponse(
                job.getId(),
                job.getServiceTypeId(),
                job.getBayId(),
                job.getStartTime(),
                job.getEndTime(),
                job.getStatus(),
                job.getBaseMinutes(),
                job.getEstimateCost());
    }
}
