package dev.hata.scheduler.scheduling.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipConfigPort.DealershipConfig;
import dev.hata.scheduler.common.provider.TechnicianAvailabilityPort;
import dev.hata.scheduler.scheduling.dto.SlotAvailabilityResponse;
import dev.hata.scheduler.scheduling.model.ServiceType;
import dev.hata.scheduler.scheduling.model.ServiceTypeTechnicianRequirement;
import dev.hata.scheduler.scheduling.model.WorkshopBay;
import dev.hata.scheduler.scheduling.repository.ServiceTypeTechnicianRequirementRepository;
import dev.hata.scheduler.scheduling.repository.WorkshopBayRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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
 *   <li>Apply augmenting-path bipartite matching so each role slot gets a distinct technician.</li>
 *   <li>Exclude slots where any role slot cannot be filled or the bay gate fails.</li>
 * </ol>
 *
 * <p>Accepts {@link DealershipConfig} from {@code common/provider} instead of the
 * {@code Dealership} entity to stay within the scheduling package boundary (ArchUnit ADR-009).
 */
@Service
public class SlotGridComputeService {

    private static final Logger log = LoggerFactory.getLogger(SlotGridComputeService.class);

    private final WorkshopBayRepository bayRepository;
    private final JdbcTemplate jdbc;
    private final ServiceTypeTechnicianRequirementRepository requirementRepository;
    private final TechnicianAvailabilityPort technicianAvailability;

    public SlotGridComputeService(
            WorkshopBayRepository bayRepository,
            JdbcTemplate jdbc,
            ServiceTypeTechnicianRequirementRepository requirementRepository,
            TechnicianAvailabilityPort technicianAvailability) {
        this.bayRepository = bayRepository;
        this.jdbc = jdbc;
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
        UUID dealershipId = config.id();

        // Past dates return no slots.
        if (date.isBefore(today)) {
            return List.of();
        }

        // Step 1: Generate candidate time slots from dealership operating hours.
        List<CandidateSlot> candidates = generateCandidates(config, serviceType, date, zone, now);
        if (candidates.isEmpty()) {
            return List.of();
        }

        // Step 2 (single load): load active bays, the day's non-cancelled jobs,
        // and the service-type requirements once. Bay overlap is then evaluated
        // in memory per candidate slot instead of issuing a per-slot repository
        // query (R-SLOT-NPLUS1).
        List<WorkshopBay> allBays = bayRepository.findActiveByDealershipId(dealershipId);
        List<ServiceJobSpan> dayJobs = loadDayJobs(dealershipId, date, zone);
        List<ServiceTypeTechnicianRequirement> requirements =
                requirementRepository.findByServiceTypeIdOrderBySlotNumber(serviceType.getId());
        List<SlotAvailabilityResponse> result = new ArrayList<>();

        for (CandidateSlot slot : candidates) {
            Instant slotStart = slot.startZdt().toInstant();
            Instant slotEnd = slot.endZdt().toInstant();

            // Step 2: Bay gate — at least one compatible active bay must be free.
            if (!isBayAvailable(serviceType, slotStart, slotEnd, allBays, dayJobs)) {
                result.add(new SlotAvailabilityResponse(
                        slot.startZdt().toOffsetDateTime(), slot.endZdt().toOffsetDateTime(), false));
                continue;
            }

            // If no technician requirements, slot is available (bay gate passed).
            if (requirements.isEmpty()) {
                result.add(new SlotAvailabilityResponse(
                        slot.startZdt().toOffsetDateTime(), slot.endZdt().toOffsetDateTime(), true));
                continue;
            }

            // Steps 3–5: Technician gate using augmenting-path bipartite matching.
            List<List<UUID>> roleCandidates = new ArrayList<>();
            for (ServiceTypeTechnicianRequirement req : requirements) {
                List<UUID> techList = technicianAvailability.findAvailableTechnicianIds(
                        dealershipId, req.getRequiredSkill(), req.getMinSkillLevel(), slotStart, slotEnd);
                roleCandidates.add(techList);
            }
            boolean available = canFillAllRoleSlots(roleCandidates);
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

            // Skip the candidate if it would not advance (e.g. cursor stuck on a
            // spring-forward gap slot) to guarantee loop termination.
            if (slotEnd.isBefore(cursor)) {
                break;
            }

            // Skip lunch overlap.
            if (!isInLunch(cursor, slotEnd, lunchStart, lunchEnd)) {
                // Skip past slots on today.
                if (!isToday || cursor.isAfter(nowLocal)) {
                    ZonedDateTime startZdt = ZonedDateTime.of(LocalDateTime.of(date, cursor), zone);
                    ZonedDateTime endZdt = ZonedDateTime.of(LocalDateTime.of(date, slotEnd), zone);
                    // DST guard (R-SLOT-DST): a spring-forward gap shifts the end
                    // instant so the slot is not exactly the service duration.
                    long elapsedSeconds =
                            java.time.Duration.between(startZdt, endZdt).getSeconds();
                    if (elapsedSeconds
                            != java.time.Duration.ofMinutes(durationMinutes).getSeconds()) {
                        // Gap-affected candidate: do not emit, but advance the
                        // cursor so the loop makes progress and terminates.
                        cursor = cursor.plusMinutes(intervalMinutes);
                        continue;
                    }
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

    /**
     * Step 2: Bay gate. At least one active bay supporting an allowed bay type
     * must have no overlapping active service job. Evaluated in memory from the
     * single per-grid load of bays and day jobs (R-SLOT-NPLUS1).
     */
    private boolean isBayAvailable(
            ServiceType serviceType,
            Instant slotStart,
            Instant slotEnd,
            List<WorkshopBay> allBays,
            List<ServiceJobSpan> dayJobs) {
        for (WorkshopBay bay : allBays) {
            if (bay.getBayTypes() == null
                    || !bay.getBayTypes().stream().anyMatch(serviceType.getAllowedBayTypes()::contains)) {
                continue;
            }
            boolean overlaps = false;
            for (ServiceJobSpan job : dayJobs) {
                if (job.bayId().equals(bay.getId())
                        && job.startTime().isBefore(slotEnd)
                        && job.endTime().isAfter(slotStart)) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                return true;
            }
        }
        return false;
    }

    /** RowMapper for {@link ServiceJobSpan}, extracted for testability (line coverage). */
    static final RowMapper<ServiceJobSpan> JOB_SPAN_ROW_MAPPER = (rs, rowNum) -> new ServiceJobSpan(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("bay_id"),
            rs.getTimestamp("start_time").toInstant(),
            rs.getTimestamp("end_time").toInstant());

    /**
     * Loads the day's active service job spans in a single query.
     *
     * @param dealershipId the dealership scope
     * @param date         the calendar date
     * @param zone         the dealership timezone
     * @return non-cancelled, non-done jobs overlapping the day
     */
    private List<ServiceJobSpan> loadDayJobs(UUID dealershipId, LocalDate date, ZoneId zone) {
        ZonedDateTime zStart = date.atStartOfDay(zone);
        ZonedDateTime zEnd = zStart.plusDays(1);
        // service_jobs links to appointments; appointent_id joins to the dealership.
        // Bind day bounds as Timestamp to satisfy the PostgreSQL driver, which
        // cannot infer the SQL type for a bare java.time.Instant (PGJDBC-1622).
        java.sql.Timestamp dayStart = java.sql.Timestamp.from(zStart.toInstant());
        java.sql.Timestamp dayEnd = java.sql.Timestamp.from(zEnd.toInstant());
        return jdbc.query(
                """
                SELECT sj.id, sj.bay_id, sj.start_time, sj.end_time
                FROM service_jobs sj
                JOIN appointments a ON a.id = sj.appointment_id
                WHERE a.dealership_id = ?
                  AND sj.status NOT IN ('CANCELLED', 'DONE')
                  AND sj.start_time < ?
                  AND sj.end_time > ?
                """,
                JOB_SPAN_ROW_MAPPER,
                dealershipId,
                dayEnd,
                dayStart);
    } // --- Steps 3–5: technician gate with augmenting-path matching ---

    /**
     * Steps 3–5: Technician gate using augmenting-path bipartite matching.
     *
     * <p>Each role slot is a node on the left, each technician is a node on the
     * right. An edge exists from a role slot to each of its candidates. We search
     * for a matching that assigns every role slot a distinct technician, using
     * Kuhn's augmenting-path algorithm.
     *
     * <p>This replaces the naive greedy (first-available) approach, which could
     * reject a fillable slot when equally-scarce roles share candidate technicians.
     *
     * @param roleCandidates list of candidate technician lists, one per role slot
     * @return true if every role slot can be assigned a distinct technician
     */
    boolean canFillAllRoleSlots(List<List<UUID>> roleCandidates) {
        int roleCount = roleCandidates.size();
        if (roleCount == 0) {
            return true;
        }

        // Build a deterministic index per technician across all candidate lists.
        List<UUID> techIndex = new ArrayList<>();
        Map<UUID, Integer> techToIndex = new HashMap<>();
        for (List<UUID> list : roleCandidates) {
            for (UUID techId : list) {
                if (!techToIndex.containsKey(techId)) {
                    techToIndex.put(techId, techIndex.size());
                    techIndex.add(techId);
                }
            }
        }

        // Build adjacency: role -> list of tech indices.
        List<List<Integer>> adj = new ArrayList<>(roleCount);
        for (int r = 0; r < roleCount; r++) {
            List<Integer> neighbors = new ArrayList<>();
            for (UUID techId : roleCandidates.get(r)) {
                Integer idx = techToIndex.get(techId);
                if (idx != null) {
                    neighbors.add(idx);
                }
            }
            adj.add(neighbors);
        }

        // -1 means no role assigned to that tech index.
        int[] matchForTech = new int[techIndex.size()];
        java.util.Arrays.fill(matchForTech, -1);

        for (int role = 0; role < roleCount; role++) {
            boolean[] visited = new boolean[techIndex.size()];
            if (!tryAugment(role, adj, matchForTech, visited)) {
                log.debug(
                        "Slot excluded: no valid technician assignment for {} role slot(s) " + "in the candidate set",
                        roleCount);
                return false;
            }
        }
        return true;
    }

    /** Kuhn's algorithm: tries to find an augmenting path for the given role. */
    @SuppressWarnings("PMD.UseVarargs")
    private boolean tryAugment(int role, List<List<Integer>> adj, int[] matchForTech, boolean[] visited) {
        for (int techIdx : adj.get(role)) {
            if (visited[techIdx]) {
                continue;
            }
            visited[techIdx] = true;
            if (matchForTech[techIdx] == -1 || tryAugment(matchForTech[techIdx], adj, matchForTech, visited)) {
                matchForTech[techIdx] = role;
                return true;
            }
        }
        return false;
    }

    // --- Internal value types ---

    /** A candidate time window for a slot, resolved in the dealership timezone. */
    private record CandidateSlot(ZonedDateTime startZdt, ZonedDateTime endZdt) {}

    /** A day's active service-job span, for in-memory bay overlap checks. */
    record ServiceJobSpan(UUID jobId, UUID bayId, Instant startTime, Instant endTime) {}
}
