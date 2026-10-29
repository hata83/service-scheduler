package dev.hata.scheduler.scheduling.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Filter input for the manager schedule history query.
 * Every filter is optional. A null {@code dealershipId} reads all dealerships for ADMIN.
 * {@code page} is zero-based; {@code size} bounds the result list.
 * {@code from} and {@code to} hold instant bounds. {@code fromDate} and {@code toDate} hold
 * date-only bounds. A global request compares the date bounds with the local date of every
 * appointment in the timezone of its own dealership.
 */
public record ScheduleHistoryQuery(
        UUID dealershipId,
        String status,
        Instant from,
        Instant to,
        LocalDate fromDate,
        LocalDate toDate,
        UUID bayId,
        String customerPhone,
        int page,
        int size) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;
    public static final int MAX_PAGE = 10_000;
}
