package dev.hata.scheduler.scheduling.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Filter input for the manager schedule history query.
 * Every filter is optional. A null {@code dealershipId} reads all dealerships for ADMIN.
 * {@code page} is zero-based; {@code size} bounds the result list. A null scope reads all dealerships for ADMIN.
 */
public record ScheduleHistoryQuery(
        UUID dealershipId,
        String status,
        Instant from,
        Instant to,
        UUID bayId,
        String customerPhone,
        int page,
        int size) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;
}
