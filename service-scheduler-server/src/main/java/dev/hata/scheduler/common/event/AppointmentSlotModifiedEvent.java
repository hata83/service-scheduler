package dev.hata.scheduler.common.event;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Event published when an appointment booking or status change alters workshop slot capacity.
 */
public record AppointmentSlotModifiedEvent(UUID dealershipId, Set<LocalDate> dates) {

    public AppointmentSlotModifiedEvent {
        dates = Set.copyOf(dates);
    }
}
