package dev.hata.scheduler.appointment.handler;

import java.time.LocalDate;

import dev.hata.scheduler.common.event.AppointmentSlotModifiedEvent;
import dev.hata.scheduler.common.provider.SlotCacheEvictionProvider;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evicts slot availability cache after appointment transaction commits (SYSTEM_DESIGN §12.1).
 */
@Component
public class AppointmentSlotEvictionListener {

    private final SlotCacheEvictionProvider slotCacheEviction;

    public AppointmentSlotEvictionListener(SlotCacheEvictionProvider slotCacheEviction) {
        this.slotCacheEviction = slotCacheEviction;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentSlotModified(AppointmentSlotModifiedEvent event) {
        for (LocalDate date : event.dates()) {
            slotCacheEviction.evictSlots(event.dealershipId(), date);
        }
    }
}
