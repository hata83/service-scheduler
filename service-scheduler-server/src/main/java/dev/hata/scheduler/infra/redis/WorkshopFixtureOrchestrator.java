package dev.hata.scheduler.infra.redis;

import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Coordinates appointment and slot cache fixture populators with a single calendar date.
 */
@Component
@Profile({"dev", "test"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkshopFixtureOrchestrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkshopFixtureOrchestrator.class);
    private static final ZoneId TIMEZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AppointmentFixturePopulator appointmentPopulator;
    private final WorkshopSlotFixturePopulator slotPopulator;

    public WorkshopFixtureOrchestrator(
            AppointmentFixturePopulator appointmentPopulator, WorkshopSlotFixturePopulator slotPopulator) {
        this.appointmentPopulator = appointmentPopulator;
        this.slotPopulator = slotPopulator;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(TIMEZONE);
        coordinate(today);
    }

    /**
     * Executes both fixture refreshes against the given reference date.
     */
    public void coordinate(LocalDate today) {
        appointmentPopulator.populate(today);
        try {
            slotPopulator.populate(today);
        } catch (Exception ex) {
            log.warn("Failed to populate demo slot availability grids in Redis: {}", ex.getMessage(), ex);
        }
    }
}
