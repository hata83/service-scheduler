package dev.hata.scheduler.scheduling.controller;

import java.util.UUID;

import dev.hata.scheduler.scheduling.service.SlotAvailabilityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev/test only. Allows the E2E suite to evict cached slot grids so availability
 * queries exercise the DB-backed gate instead of preloaded fixture grids.
 *
 * <p>Restricted to the {@code dev} and {@code test} profiles. Production never
 * loads this controller. The endpoint is also unauthenticated by design: it
 * only exists because it is unreachable outside dev/test.
 */
@Tag(name = "Slots", description = "Workshop time slot availability operations")
@RestController
@RequestMapping("/api/v1/slots")
@Profile({"dev", "test"})
public class SlotGridCacheAdminController {

    private final SlotAvailabilityService slotService;

    public SlotGridCacheAdminController(SlotAvailabilityService slotService) {
        this.slotService = slotService;
    }

    /**
     * Dev/test only. Clears every cached slot grid for a dealership so the next
     * availability request recomputes from live database state.
     */
    @Operation(
            summary = "Clear cached slot grids",
            description = "Dev/test only. Evicts all cached slot grids for a dealership.",
            security = {})
    @DeleteMapping("/cache")
    public ResponseEntity<Void> clearCachedSlots(
            @Parameter(description = "Dealership ID", required = true) @RequestParam("dealershipId")
                    UUID dealershipId) {
        slotService.evictSlotsByDealership(dealershipId);
        return ResponseEntity.noContent().build();
    }
}
