package dev.hata.scheduler.appointment.handler;

import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import dev.hata.scheduler.common.event.AppointmentSlotModifiedEvent;
import dev.hata.scheduler.common.provider.SlotCacheEvictionProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentSlotEvictionListenerTest {

    @Mock
    private SlotCacheEvictionProvider slotCacheEviction;

    @InjectMocks
    private AppointmentSlotEvictionListener listener;

    @Test
    void onAppointmentSlotModifiedEvictsEachDate() {
        UUID dealershipId = UUID.randomUUID();
        LocalDate d1 = LocalDate.of(2026, 11, 25);
        LocalDate d2 = LocalDate.of(2026, 11, 26);
        AppointmentSlotModifiedEvent event = new AppointmentSlotModifiedEvent(dealershipId, Set.of(d1, d2));

        listener.onAppointmentSlotModified(event);

        verify(slotCacheEviction).evictSlots(dealershipId, d1);
        verify(slotCacheEviction).evictSlots(dealershipId, d2);
    }
}
