package dev.hata.scheduler.appointment.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response carrying the persisted inspection notes for a service job.
 */
public record JobNoteResponse(UUID jobId, List<String> notes) {

    public static JobNoteResponse of(UUID jobId, String notes) {
        if (notes == null || notes.isBlank()) {
            return new JobNoteResponse(jobId, List.of());
        }
        return new JobNoteResponse(
                jobId,
                java.util.Arrays.stream(notes.split("\n", -1))
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .toList());
    }
}
