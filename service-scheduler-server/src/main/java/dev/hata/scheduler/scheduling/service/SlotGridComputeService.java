package dev.hata.scheduler.scheduling.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipConfigPort.DealershipConfig;
import dev.hata.scheduler.common.provider.TechnicianAvailabilityPort;
import dev.hata.scheduler.scheduling.dto.SlotAvailabilityResponse;
import dev.hata.scheduler.scheduling.model.ServiceType;
import dev.hata.scheduler.scheduling.model.ServiceTypeTechnicianRequirement;
import dev.hata.scheduler.scheduling.repository.ServiceTypeTechnicianRequirementRepository;
import dev.hata.scheduler.scheduling.repository.WorkshopBayRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the available slot grid for a service type and date.
 *
 * <p>Implements the 5-step dual-resource gating algorithm from SYSTEM_DESIGN §5.4:
 * <ol>
 *   <li>Generate candidate time slots from dealership operating hours.</li>
 *   <li>Filter bays by {@code allowed_bay_types} with no overlapping active service jobs.</li>
 *   <li>Find qualified technicians with no overlapping assignments.</li>
 *   <li>Apply greedy scarcest-slot technician matching.</li>
 *   <li>Exclude slots where any role slot cannot be filled.</li>
 * </ol>
 *
 * <p>Accepts {@link DealershipConfig} from {@code common/provider} instead of the
 * {@code Dealership} entity to stay within the scheduling package boundary (ArchUnit ADR-009).
 */
@Service
public class SlotGridComputeService {

    private static final Logger log = LoggerFactory.getLogger(SlotGridComputeService.class);

    private final WorkshopBayRepository bayRepository;
    private final ServiceTypeTechnicianRequirementRepository requirementRepository;
    private final TechnicianAvailabilityPort technicianAvailability;

    public SlotGridComputeService(
            WorkshopBayRepository bayRepository,
            ServiceTypeTechnicianRequirementRepository requirementRepository,
            TechnicianAvailabilityPort technicianAvailability) {
        this.bayRepository = bayRepository;
        this.requirementRepository = requirementRepository;
        this.technicianAvailability = technicianAvailability;
    }

    /**
     * Computes available slots for the given dealership config, service type, and date.
     *
     * @param config      dealership scheduling configuration
     * @param serviceType the service type being queried
     * @param date        the calendar date to evaluate
     * @param now         the current instant (used to skip past slots on today)
     * @return list of slot windows with availability flag set
     */
    @Transactional(readOnly = true)
    public List<SlotAvailabilityResponse> compute(
            DealershipConfig config, ServiceType serviceType, LocalDate date, Instant now) {

        ZoneId zone = ZoneId.of(config.timezone());
        LocalDate today = now.atZone(zone).toLocalDate();

        // Past dates return no slots.
        if (date.isBefore(today)) {
            return List.of();
        }

        // Step 1: Generate candidate slots from dealership operating hours.
        List<CandidateSlot> candidates = generateCandidates(config, serviceType, date, zone, now);
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<ServiceTypeTechnicianRequirement> requirements =
                requirementRepository.findByServiceTypeIdOrderBySlotNumber(serviceType.getId());

        List<SlotAvailabilityResponse> result = new ArrayList<>();
        for (CandidateSlot slot : candidates) {
            boolean available = evaluateSlot(config.id(), serviceType, slot, requirements);
            result.add(new SlotAvailabilityResponse(
                    slot.startZdt().toOffsetDateTime(), slot.endZdt().toOffsetDateTime(), available));
        }
        return result;
    }

    // --- Step 1: generate candidate time slots ---

    private List<CandidateSlot> generateCandidates(
            DealershipConfig config, ServiceType serviceType, LocalDate date, ZoneId zone, Instant now) {

        int durationMinutes = serviceType.getEstimatedMinutes();
        int intervalMinutes = config.slotIntervalMinutes();
        LocalTime open = config.openTime();
        LocalTime close = config.closeTime();
        LocalTime lunchStart = config.lunchStart();
        LocalTime lunchEnd = config.lunchEnd();

        LocalTime nowLocal = now.atZone(zone).toLocalTime();
        LocalDate nowDate = now.atZone(zone).toLocalDate();
        boolean isToday = date.equals(nowDate);

        List<CandidateSlot> slots = new ArrayList<>();
        LocalTime cursor = open;

        while (true) {
            LocalTime slotEnd = cursor.plusMinutes(durationMinutes);

            // Slot must end by close time.
            if (slotEnd.isAfter(close)) {
                break;
            }

            // Skip lunch overlap.
            if (!isInLunch(cursor, slotEnd, lunchStart, lunchEnd)) {
                // Skip past slots on today.
                if (!isToday || cursor.isAfter(nowLocal)) {
                    ZonedDateTime startZdt = ZonedDateTime.of(LocalDateTime.of(date, cursor), zone);
                    ZonedDateTime endZdt = ZonedDateTime.of(LocalDateTime.of(date, slotEnd), zone);
                    slots.add(new CandidateSlot(startZdt, endZdt));
                }
            }

            // Advance by slot interval.
            cursor = cursor.plusMinutes(intervalMinutes);
            if (!cursor.isBefore(close)) {
                break;
            }
        }
        return slots;
    }

    private boolean isInLunch(LocalTime start, LocalTime end, LocalTime lunchStart, LocalTime lunchEnd) {
        if (lunchStart == null || lunchEnd == null) {
            return false;
        }
        // Overlaps if start < lunchEnd AND end > lunchStart.
        return start.isBefore(lunchEnd) && end.isAfter(lunchStart);
    }

    // --- Steps 2–5: evaluate a single candidate slot ---

    private boolean evaluateSlot(
            UUID dealershipId,
            ServiceType serviceType,
            CandidateSlot slot,
            List<ServiceTypeTechnicianRequirement> requirements) {

        Instant slotStart = slot.startZdt().toInstant();
        Instant slotEnd = slot.endZdt().toInstant();

        // Step 2: Bay gate — at least one compatible bay must be free.
        if (!isBayAvailable(dealershipId, serviceType, slotStart, slotEnd)) {
            return false;
        }

        // If no technician requirements, slot is available (bay gate passed).
        if (requirements.isEmpty()) {
            return true;
        }

        // Steps 3–5: Technician gate using scarcest-slot-first greedy matching.
        return canFillAllRoleSlots(dealershipId, requirements, slotStart, slotEnd);
    }

    /**
     * Step 2: Bay gate. At least one active bay with any of the allowed bay types
     * must have no overlapping active service job.
     */
    private boolean isBayAvailable(UUID dealershipId, ServiceType serviceType, Instant slotStart, Instant slotEnd) {
        for (String bayType : serviceType.getAllowedBayTypes()) {
            if (!bayRepository
                    .findAvailableBays(dealershipId, bayType, slotStart, slotEnd)
                    .isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Steps 3–5: Technician gate using scarcest-slot-first greedy matching.
     *
     * <p>Sorts role slots by candidate count ascending (scarcest first).
     * For each slot in that order, assigns the first unassigned candidate.
     * Returns false if any slot cannot be filled.
     */
    private boolean canFillAllRoleSlots(
            UUID dealershipId,
            List<ServiceTypeTechnicianRequirement> requirements,
            Instant slotStart,
            Instant slotEnd) {

        // Step 3: Build candidates per role slot.
        List<RoleSlotCandidates> roleSlots = new ArrayList<>();
        for (ServiceTypeTechnicianRequirement req : requirements) {
            List<UUID> candidates = technicianAvailability.findAvailableTechnicianIds(
                    dealershipId, req.getRequiredSkill(), req.getMinSkillLevel(), slotStart, slotEnd);
            roleSlots.add(new RoleSlotCandidates(req.getSlotNumber(), candidates));
        }

        // Step 4: Sort by candidate count ascending (scarcest slot first).
        roleSlots.sort(Comparator.comparingInt(rs -> rs.candidates().size()));

        // Step 5: Greedy assignment — each technician fills at most one role slot.
        Set<UUID> assigned = new HashSet<>();
        for (RoleSlotCandidates roleSlot : roleSlots) {
            UUID chosen = null;
            for (UUID techId : roleSlot.candidates()) {
                if (!assigned.contains(techId)) {
                    chosen = techId;
                    break;
                }
            }
            if (chosen == null) {
                log.debug(
                        "Slot excluded: no technician available for role slot {} in window [{}, {}]",
                        roleSlot.slotNumber(),
                        slotStart,
                        slotEnd);
                return false;
            }
            assigned.add(chosen);
        }
        return true;
    }

    // --- Internal value types ---

    private record CandidateSlot(ZonedDateTime startZdt, ZonedDateTime endZdt) {}

    private record RoleSlotCandidates(int slotNumber, List<UUID> candidates) {}
}
