package dev.hata.scheduler.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipConfigPort.DealershipConfig;
import dev.hata.scheduler.common.provider.TechnicianAvailabilityPort;
import dev.hata.scheduler.scheduling.dto.SlotAvailabilityResponse;
import dev.hata.scheduler.scheduling.model.ServiceType;
import dev.hata.scheduler.scheduling.model.ServiceTypeTechnicianRequirement;
import dev.hata.scheduler.scheduling.model.WorkshopBay;
import dev.hata.scheduler.scheduling.repository.ServiceTypeTechnicianRequirementRepository;
import dev.hata.scheduler.scheduling.repository.WorkshopBayRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlotGridComputeServiceTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SERVICE_TYPE_ID = UUID.fromString("22222222-0000-0000-0000-000000000002");
    private static final UUID TECH_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Mock
    private WorkshopBayRepository bayRepository;

    @Mock
    private ServiceTypeTechnicianRequirementRepository requirementRepository;

    @Mock
    private TechnicianAvailabilityPort technicianAvailability;

    private SlotGridComputeService service;

    private DealershipConfig config;

    @BeforeEach
    void setUp() {
        service = new SlotGridComputeService(bayRepository, requirementRepository, technicianAvailability);
        config = new DealershipConfig(
                DEALERSHIP_ID,
                "Asia/Ho_Chi_Minh",
                60,
                LocalTime.of(8, 0),
                LocalTime.of(17, 30),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                7,
                3600);
    }

    // --- Past date ---

    @Test
    void computeReturnEmptySlotsForPastDate() {
        Instant now = Instant.parse("2026-10-27T10:00:00Z");
        LocalDate yesterday = LocalDate.of(2026, 10, 26);
        ServiceType st = buildServiceType(60, List.of("STANDARD"));

        List<SlotAvailabilityResponse> result = service.compute(config, st, yesterday, now);

        assertThat(result).isEmpty();
    }

    // --- Bay gate ---

    @Test
    void computeReturnUnavailableWhenNoBayFreeForAnyBayType() {
        Instant now = Instant.parse("2026-10-27T01:00:00Z"); // 08:00 Ho_Chi_Minh
        LocalDate tomorrow = LocalDate.of(2026, 10, 28);
        ServiceType st = buildServiceType(60, List.of("STANDARD"));

        when(requirementRepository.findByServiceTypeIdOrderBySlotNumber(SERVICE_TYPE_ID))
                .thenReturn(List.of());
        when(bayRepository.findAvailableBays(eq(DEALERSHIP_ID), eq("STANDARD"), any(), any()))
                .thenReturn(List.of()); // all bays occupied

        List<SlotAvailabilityResponse> result = service.compute(config, st, tomorrow, now);

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(s -> !s.available());
    }

    @Test
    void computeReturnAvailableWhenBayFreeAndNoTechRequirements() {
        Instant now = Instant.parse("2026-10-27T01:00:00Z");
        LocalDate tomorrow = LocalDate.of(2026, 10, 28);
        ServiceType st = buildServiceType(60, List.of("STANDARD"));

        when(requirementRepository.findByServiceTypeIdOrderBySlotNumber(SERVICE_TYPE_ID))
                .thenReturn(List.of());
        WorkshopBay bay = new WorkshopBay();
        when(bayRepository.findAvailableBays(eq(DEALERSHIP_ID), eq("STANDARD"), any(), any()))
                .thenReturn(List.of(bay));

        List<SlotAvailabilityResponse> result = service.compute(config, st, tomorrow, now);

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(SlotAvailabilityResponse::available);
    }

    // --- Technician gate ---

    @Test
    void computeReturnUnavailableWhenNoTechnicianMeetsRequirement() {
        Instant now = Instant.parse("2026-10-27T01:00:00Z");
        LocalDate tomorrow = LocalDate.of(2026, 10, 28);
        ServiceType st = buildServiceType(60, List.of("STANDARD"));

        ServiceTypeTechnicianRequirement req = buildRequirement(SERVICE_TYPE_ID, 1, "BRAKES", "TECHNICIAN");
        when(requirementRepository.findByServiceTypeIdOrderBySlotNumber(SERVICE_TYPE_ID))
                .thenReturn(List.of(req));
        WorkshopBay bay = new WorkshopBay();
        when(bayRepository.findAvailableBays(eq(DEALERSHIP_ID), eq("STANDARD"), any(), any()))
                .thenReturn(List.of(bay));
        when(technicianAvailability.findAvailableTechnicianIds(
                        eq(DEALERSHIP_ID), eq("BRAKES"), eq("TECHNICIAN"), any(), any()))
                .thenReturn(List.of()); // no technician available

        List<SlotAvailabilityResponse> result = service.compute(config, st, tomorrow, now);

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(s -> !s.available());
    }

    @Test
    void computeReturnAvailableWhenTechnicianMeetsRequirement() {
        Instant now = Instant.parse("2026-10-27T01:00:00Z");
        LocalDate tomorrow = LocalDate.of(2026, 10, 28);
        ServiceType st = buildServiceType(60, List.of("STANDARD"));

        ServiceTypeTechnicianRequirement req = buildRequirement(SERVICE_TYPE_ID, 1, "BRAKES", "TECHNICIAN");
        when(requirementRepository.findByServiceTypeIdOrderBySlotNumber(SERVICE_TYPE_ID))
                .thenReturn(List.of(req));
        WorkshopBay bay = new WorkshopBay();
        when(bayRepository.findAvailableBays(eq(DEALERSHIP_ID), eq("STANDARD"), any(), any()))
                .thenReturn(List.of(bay));
        when(technicianAvailability.findAvailableTechnicianIds(
                        eq(DEALERSHIP_ID), eq("BRAKES"), eq("TECHNICIAN"), any(), any()))
                .thenReturn(List.of(TECH_A));

        List<SlotAvailabilityResponse> result = service.compute(config, st, tomorrow, now);

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(SlotAvailabilityResponse::available);
    }

    // --- Multi-slot greedy: one technician cannot fill two role slots ---

    @Test
    void computeReturnUnavailableWhenSingleTechnicianCannotFillTwoRoleSlots() {
        Instant now = Instant.parse("2026-10-27T01:00:00Z");
        LocalDate tomorrow = LocalDate.of(2026, 10, 28);
        ServiceType st = buildServiceType(60, List.of("HEAVY_LINE"));
        DealershipConfig heavyConfig = new DealershipConfig(
                DEALERSHIP_ID,
                "Asia/Ho_Chi_Minh",
                60,
                LocalTime.of(8, 0),
                LocalTime.of(17, 30),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                7,
                3600);

        ServiceTypeTechnicianRequirement slot1 = buildRequirement(SERVICE_TYPE_ID, 1, "ENGINE", "MASTER");
        ServiceTypeTechnicianRequirement slot2 = buildRequirement(SERVICE_TYPE_ID, 2, "ENGINE", "SENIOR");
        when(requirementRepository.findByServiceTypeIdOrderBySlotNumber(SERVICE_TYPE_ID))
                .thenReturn(List.of(slot1, slot2));
        WorkshopBay bay = new WorkshopBay();
        when(bayRepository.findAvailableBays(eq(DEALERSHIP_ID), eq("HEAVY_LINE"), any(), any()))
                .thenReturn(List.of(bay));
        // Only one technician available for both slots.
        when(technicianAvailability.findAvailableTechnicianIds(
                        eq(DEALERSHIP_ID), eq("ENGINE"), eq("MASTER"), any(), any()))
                .thenReturn(List.of(TECH_A));
        when(technicianAvailability.findAvailableTechnicianIds(
                        eq(DEALERSHIP_ID), eq("ENGINE"), eq("SENIOR"), any(), any()))
                .thenReturn(List.of(TECH_A)); // same technician

        List<SlotAvailabilityResponse> result = service.compute(heavyConfig, st, tomorrow, now);

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(s -> !s.available());
    }

    @Test
    void computeReturnAvailableWhenTwoDistinctTechniciansCanFillTwoRoleSlots() {
        Instant now = Instant.parse("2026-10-27T01:00:00Z");
        LocalDate tomorrow = LocalDate.of(2026, 10, 28);
        ServiceType st = buildServiceType(60, List.of("HEAVY_LINE"));
        UUID techB = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
        DealershipConfig heavyConfig = new DealershipConfig(
                DEALERSHIP_ID,
                "Asia/Ho_Chi_Minh",
                60,
                LocalTime.of(8, 0),
                LocalTime.of(17, 30),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                7,
                3600);

        ServiceTypeTechnicianRequirement slot1 = buildRequirement(SERVICE_TYPE_ID, 1, "ENGINE", "MASTER");
        ServiceTypeTechnicianRequirement slot2 = buildRequirement(SERVICE_TYPE_ID, 2, "ENGINE", "SENIOR");
        when(requirementRepository.findByServiceTypeIdOrderBySlotNumber(SERVICE_TYPE_ID))
                .thenReturn(List.of(slot1, slot2));
        WorkshopBay bay = new WorkshopBay();
        when(bayRepository.findAvailableBays(eq(DEALERSHIP_ID), eq("HEAVY_LINE"), any(), any()))
                .thenReturn(List.of(bay));
        when(technicianAvailability.findAvailableTechnicianIds(
                        eq(DEALERSHIP_ID), eq("ENGINE"), eq("MASTER"), any(), any()))
                .thenReturn(List.of(TECH_A));
        when(technicianAvailability.findAvailableTechnicianIds(
                        eq(DEALERSHIP_ID), eq("ENGINE"), eq("SENIOR"), any(), any()))
                .thenReturn(List.of(TECH_A, techB));

        List<SlotAvailabilityResponse> result = service.compute(heavyConfig, st, tomorrow, now);

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(SlotAvailabilityResponse::available);
    }

    // --- Lunch break exclusion ---

    @Test
    void computeExcludesSlotsThatOverlapLunchBreak() {
        Instant now = Instant.parse("2026-10-27T01:00:00Z");
        LocalDate tomorrow = LocalDate.of(2026, 10, 28);
        ServiceType st = buildServiceType(60, List.of("STANDARD"));

        when(requirementRepository.findByServiceTypeIdOrderBySlotNumber(SERVICE_TYPE_ID))
                .thenReturn(List.of());
        WorkshopBay bay = new WorkshopBay();
        when(bayRepository.findAvailableBays(eq(DEALERSHIP_ID), eq("STANDARD"), any(), any()))
                .thenReturn(List.of(bay));

        List<SlotAvailabilityResponse> result = service.compute(config, st, tomorrow, now);

        // No slot should start in the lunch window (12:00–13:00).
        result.forEach(s -> {
            int startHour = s.startTime().getHour();
            assertThat(startHour >= 12 && startHour < 13).isFalse();
        });
    }

    // --- Duration gate: slots that exceed close time are excluded ---

    @Test
    void computeExcludesSlotThatWouldExceedCloseTime() {
        Instant now = Instant.parse("2026-10-27T01:00:00Z");
        LocalDate tomorrow = LocalDate.of(2026, 10, 28);
        // 120-minute service; close time 17:30 — last valid start is 15:30
        ServiceType st = buildServiceType(120, List.of("STANDARD"));

        when(requirementRepository.findByServiceTypeIdOrderBySlotNumber(SERVICE_TYPE_ID))
                .thenReturn(List.of());
        WorkshopBay bay = new WorkshopBay();
        when(bayRepository.findAvailableBays(eq(DEALERSHIP_ID), eq("STANDARD"), any(), any()))
                .thenReturn(List.of(bay));

        List<SlotAvailabilityResponse> result = service.compute(config, st, tomorrow, now);

        // Every slot end must be at or before 17:30.
        result.forEach(s -> {
            int endHour = s.endTime().getHour();
            int endMin = s.endTime().getMinute();
            assertThat(endHour < 17 || (endHour == 17 && endMin <= 30)).isTrue();
        });
    }

    // --- No-lunch config produces slots across the full day ---

    @Test
    void computeGeneratesSlotsAcrossFullDayWhenNoLunchConfigured() {
        DealershipConfig noLunch = new DealershipConfig(
                DEALERSHIP_ID, "Asia/Ho_Chi_Minh", 60, LocalTime.of(8, 0), LocalTime.of(17, 30), null, null, 7, 3600);
        Instant now = Instant.parse("2026-10-27T01:00:00Z");
        LocalDate tomorrow = LocalDate.of(2026, 10, 28);
        ServiceType st = buildServiceType(60, List.of("STANDARD"));

        when(requirementRepository.findByServiceTypeIdOrderBySlotNumber(SERVICE_TYPE_ID))
                .thenReturn(List.of());
        WorkshopBay bay = new WorkshopBay();
        when(bayRepository.findAvailableBays(eq(DEALERSHIP_ID), eq("STANDARD"), any(), any()))
                .thenReturn(List.of(bay));

        List<SlotAvailabilityResponse> result = service.compute(noLunch, st, tomorrow, now);

        // With no lunch and 60-min slots from 08:00 to 17:30, should have 9 slots (08-17).
        assertThat(result).hasSize(9);
        assertThat(result).allMatch(SlotAvailabilityResponse::available);
    }

    // --- Cursor reaches close after interval advance (second while-loop break) ---

    @Test
    void computeGeneratesOneSlotWhenCursorReachesCloseAfterInterval() {
        // open=08:00, close=09:00, interval=60, duration=60 -> one slot 08:00-09:00,
        // then cursor advances to 09:00 == close so the second break fires.
        DealershipConfig tight = new DealershipConfig(
                DEALERSHIP_ID, "Asia/Ho_Chi_Minh", 60, LocalTime.of(8, 0), LocalTime.of(9, 0), null, null, 7, 3600);
        Instant now = Instant.parse("2026-10-27T01:00:00Z");
        LocalDate tomorrow = LocalDate.of(2026, 10, 28);
        ServiceType st = buildServiceType(60, List.of("STANDARD"));

        when(requirementRepository.findByServiceTypeIdOrderBySlotNumber(SERVICE_TYPE_ID))
                .thenReturn(List.of());
        WorkshopBay bay = new WorkshopBay();
        when(bayRepository.findAvailableBays(eq(DEALERSHIP_ID), eq("STANDARD"), any(), any()))
                .thenReturn(List.of(bay));

        List<SlotAvailabilityResponse> result = service.compute(tight, st, tomorrow, now);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).available()).isTrue();
    }

    // --- No slots generated when service duration exceeds operating window ---

    @Test
    void computeReturnsEmptyWhenServiceDurationExceedsEntireOperatingWindow() {
        // 600-minute service on a 90-minute window -> no slot fits -> candidates empty.
        DealershipConfig narrow = new DealershipConfig(
                DEALERSHIP_ID, "Asia/Ho_Chi_Minh", 30, LocalTime.of(8, 0), LocalTime.of(9, 30), null, null, 7, 3600);
        Instant now = Instant.parse("2026-10-27T01:00:00Z");
        LocalDate tomorrow = LocalDate.of(2026, 10, 28);
        ServiceType st = buildServiceType(600, List.of("STANDARD"));

        List<SlotAvailabilityResponse> result = service.compute(narrow, st, tomorrow, now);

        assertThat(result).isEmpty();
    }

    // --- Regression: augmenting-path matching fills slots the greedy approach rejects ---

    @Test
    void canFillAllRoleSlotsWithOverlappingCandidateSetsFindsADistinctAssignment() {
        // role slot 0 = {T1, T2}, slot 1 = {T2, T3}, slot 2 = {T1, T2}.
        // Greedy (fill in order, first free) can exhaust the shared technicians and
        // reject the slot, but a complete matching exists: T1->slot0, T3->slot1, T2->slot2.
        UUID tech1 = UUID.fromString("11111111-0000-0000-0000-000000000001");
        UUID tech2 = UUID.fromString("22222222-0000-0000-0000-000000000001");
        UUID tech3 = UUID.fromString("33333333-0000-0000-0000-000000000001");
        List<List<UUID>> candidates = List.of(List.of(tech1, tech2), List.of(tech2, tech3), List.of(tech1, tech2));

        assertThat(service.canFillAllRoleSlots(candidates)).isTrue();
    }

    @Test
    void canFillAllRoleSlotsRejectsWhenNoDistinctAssignmentExists() {
        // Two role slots, single shared technician — cannot assign distinct techs.
        UUID tech1 = UUID.fromString("11111111-0000-0000-0000-000000000001");
        List<List<UUID>> candidates = List.of(List.of(tech1), List.of(tech1));

        assertThat(service.canFillAllRoleSlots(candidates)).isFalse();
    }

    @Test
    void canFillAllRoleSlotsAcceptsEmptyRoleList() {
        assertThat(service.canFillAllRoleSlots(List.of())).isTrue();
    }

    // --- Midnight wrap: long service cannot start late and wrap past midnight ---

    @Test
    void computeDoesNotGenerateSlotsThatWrapPastMidnight() {
        // 120-minute service starting at 23:00 would end at 01:00 (next day) —
        // such a candidate must be skipped, not looped. Closing at 23:30.
        DealershipConfig evening = new DealershipConfig(
                DEALERSHIP_ID, "Asia/Ho_Chi_Minh", 30, LocalTime.of(8, 0), LocalTime.of(23, 30), null, null, 7, 3600);
        Instant now = Instant.parse("2026-10-27T01:00:00Z");
        LocalDate tomorrow = LocalDate.of(2026, 10, 28);
        ServiceType st = buildServiceType(120, List.of("STANDARD"));

        List<SlotAvailabilityResponse> result = service.compute(evening, st, tomorrow, now);

        // No 120-min slot fits in 8:00-23:30 without wrapping; at most the day's
        // in-range non-wrapping slots (none here, since every candidate starting
        // late enough to wrap is dropped and earlier ones end by close).
        assertThat(result).isNotEmpty();
        for (SlotAvailabilityResponse slot : result) {
            // endTime must be on the same date as startTime.
            assertThat(slot.endTime().getDayOfMonth())
                    .isEqualTo(slot.startTime().getDayOfMonth());
        }
    }

    // --- Builders ---

    private ServiceType buildServiceType(int durationMinutes, List<String> allowedBayTypes) {
        try {
            ServiceType st = new ServiceType();
            set(st, "id", SERVICE_TYPE_ID);
            set(st, "dealershipId", DEALERSHIP_ID);
            set(st, "estimatedMinutes", durationMinutes);
            set(st, "allowedBayTypes", new java.util.ArrayList<>(allowedBayTypes));
            set(st, "active", true);
            return st;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build test ServiceType", ex);
        }
    }

    private ServiceTypeTechnicianRequirement buildRequirement(
            UUID serviceTypeId, int slotNumber, String skill, String minLevel) {
        try {
            ServiceTypeTechnicianRequirement r = new ServiceTypeTechnicianRequirement();
            set(r, "id", UUID.randomUUID());
            set(r, "serviceTypeId", serviceTypeId);
            set(r, "slotNumber", slotNumber);
            set(r, "requiredSkill", skill);
            set(r, "minSkillLevel", minLevel);
            return r;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build test requirement", ex);
        }
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private void set(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field f = findField(target.getClass(), fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException ex) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), name);
            }
            throw ex;
        }
    }
}
