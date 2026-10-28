package dev.hata.scheduler.scheduling.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response for the manager day schedule view
 * (GET /api/v1/schedule/day, change manager-schedule-realtime).
 */
public record ScheduleDayResponse(UUID dealershipId, LocalDate date, List<ScheduleBayColumn> bays) {}
