package dev.hata.scheduler.scheduling.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the manager schedule history list
 * (GET /api/v1/schedule/history, change manager-schedule-realtime).
 */
public record ScheduleHistoryItem(
        UUID id,
        UUID dealershipId,
        UUID customerId,
        String customerPhone,
        String customerName,
        String vehicleVin,
        String status,
        Instant startTime,
        Instant endTime,
        BigDecimal estimateCost,
        BigDecimal totalCost) {}
