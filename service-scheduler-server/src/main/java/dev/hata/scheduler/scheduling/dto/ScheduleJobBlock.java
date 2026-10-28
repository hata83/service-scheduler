package dev.hata.scheduler.scheduling.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One service job block inside a bay column of the manager day schedule grid.
 */
public record ScheduleJobBlock(
        UUID jobId,
        UUID appointmentId,
        String status,
        Instant startTime,
        Instant endTime,
        String customerName,
        String customerPhone,
        String vehicleVin,
        String serviceName) {}
