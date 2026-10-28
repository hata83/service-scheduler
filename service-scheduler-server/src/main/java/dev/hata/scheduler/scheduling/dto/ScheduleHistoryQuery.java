package dev.hata.scheduler.scheduling.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Filter input for the manager schedule history query.
 * Every filter is optional. A null {@code dealershipId} reads all dealerships for ADMIN.
 */
public record ScheduleHistoryQuery(
        UUID dealershipId, String status, Instant from, Instant to, UUID bayId, String customerPhone) {}
