package dev.hata.scheduler.scheduling.dto;

import java.util.List;
import java.util.UUID;

/**
 * One bay column in the manager day schedule grid.
 */
public record ScheduleBayColumn(
        UUID bayId, String bayName, List<String> bayTypes, boolean active, List<ScheduleJobBlock> jobs) {}
