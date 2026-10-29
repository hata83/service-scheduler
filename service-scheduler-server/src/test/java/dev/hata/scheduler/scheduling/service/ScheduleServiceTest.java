package dev.hata.scheduler.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipValidationProvider;
import dev.hata.scheduler.scheduling.dto.ScheduleDayResponse;
import dev.hata.scheduler.scheduling.dto.ScheduleHistoryItem;
import dev.hata.scheduler.scheduling.dto.ScheduleHistoryQuery;
import dev.hata.scheduler.scheduling.repository.ScheduleQueryRepository;
import dev.hata.scheduler.scheduling.repository.ScheduleQueryRepository.HistoryRow;
import dev.hata.scheduler.scheduling.repository.ScheduleQueryRepository.ScheduleRow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for the manager schedule board service.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BAY_1 = UUID.fromString("33333333-0000-0000-0000-000000000001");
    private static final UUID BAY_2 = UUID.fromString("33333333-0000-0000-0000-000000000002");
    private static final LocalDate DATE = LocalDate.of(2026, 10, 28);

    @Mock
    private ScheduleQueryRepository scheduleQueryRepository;

    @Mock
    private DealershipValidationProvider dealershipValidation;

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(scheduleQueryRepository, dealershipValidation);
    }

    @Test
    void findDayScheduleGroupsJobsByBay() {
        when(dealershipValidation.getDealershipTimezone(DEALERSHIP_ID)).thenReturn("Asia/Ho_Chi_Minh");

        UUID appointmentOne = UUID.randomUUID();
        UUID appointmentTwo = UUID.randomUUID();
        UUID jobOne = UUID.randomUUID();
        UUID jobTwo = UUID.randomUUID();

        List<ScheduleRow> rows = List.of(
                row(BAY_1, "Bay 1", jobOne, appointmentOne, "CONFIRMED"),
                row(BAY_2, "Bay 2", jobTwo, appointmentTwo, "REQUESTED"));
        when(scheduleQueryRepository.findDayRows(any(), any(), any())).thenReturn(rows);

        ScheduleDayResponse response = scheduleService.findDaySchedule(DEALERSHIP_ID, DATE);

        assertThat(response.dealershipId()).isEqualTo(DEALERSHIP_ID);
        assertThat(response.date()).isEqualTo(DATE);
        assertThat(response.bays()).hasSize(2);
        assertThat(response.bays().get(0).bayId()).isEqualTo(BAY_1);
        assertThat(response.bays().get(0).bayName()).isEqualTo("Bay 1");
        assertThat(response.bays().get(0).jobs()).hasSize(1);
        assertThat(response.bays().get(0).jobs().get(0).appointmentId()).isEqualTo(appointmentOne);
        assertThat(response.bays().get(0).jobs().get(0).status()).isEqualTo("CONFIRMED");
        assertThat(response.bays().get(0).jobs().get(0).customerName()).isEqualTo("Demo Customer");
        assertThat(response.bays().get(0).jobs().get(0).customerPhone()).isEqualTo("+84900000004");
        assertThat(response.bays().get(1).jobs().get(0).status()).isEqualTo("REQUESTED");
    }

    @Test
    void findDayScheduleKeepsBaysWithoutJobs() {
        when(dealershipValidation.getDealershipTimezone(DEALERSHIP_ID)).thenReturn("Asia/Ho_Chi_Minh");

        List<ScheduleRow> rows = List.of(emptyBayRow(BAY_1, "Bay 1"));
        when(scheduleQueryRepository.findDayRows(any(), any(), any())).thenReturn(rows);
        ScheduleDayResponse response = scheduleService.findDaySchedule(DEALERSHIP_ID, DATE);

        assertThat(response.bays()).hasSize(1);
        assertThat(response.bays().get(0).jobs()).isEmpty();
    }

    @Test
    void dealershipTimezoneDelegatesToProvider() {
        when(dealershipValidation.getDealershipTimezone(DEALERSHIP_ID)).thenReturn("Asia/Ho_Chi_Minh");
        assertThat(scheduleService.dealershipTimezone(DEALERSHIP_ID)).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    void findDayScheduleHandlesNullBayTypesAndNullNames() {
        when(dealershipValidation.getDealershipTimezone(DEALERSHIP_ID)).thenReturn("Asia/Ho_Chi_Minh");

        ScheduleRow rowNullTypes = org.mockito.Mockito.mock(ScheduleRow.class);
        when(rowNullTypes.getBayId()).thenReturn(BAY_1);
        when(rowNullTypes.getBayName()).thenReturn("Bay 1");
        when(rowNullTypes.getBayTypes()).thenReturn(null);
        when(rowNullTypes.isActive()).thenReturn(true);
        when(rowNullTypes.getJobId()).thenReturn(UUID.randomUUID());
        when(rowNullTypes.getAppointmentId()).thenReturn(UUID.randomUUID());
        when(rowNullTypes.getStatus()).thenReturn("CONFIRMED");
        when(rowNullTypes.getStartTime()).thenReturn(Instant.parse("2026-10-28T02:00:00Z"));
        when(rowNullTypes.getEndTime()).thenReturn(Instant.parse("2026-10-28T03:00:00Z"));
        when(rowNullTypes.getFirstName()).thenReturn(null);
        when(rowNullTypes.getLastName()).thenReturn(null);
        when(rowNullTypes.getPhone()).thenReturn("+84900000004");
        when(rowNullTypes.getVehicleVin()).thenReturn("1HGCR2F83HA000001");
        when(rowNullTypes.getServiceTypeName()).thenReturn("STANDARD_SERVICE");

        ScheduleRow rowPartialName = org.mockito.Mockito.mock(ScheduleRow.class);
        when(rowPartialName.getBayId()).thenReturn(BAY_2);
        when(rowPartialName.getBayName()).thenReturn("Bay 2");
        when(rowPartialName.getBayTypes()).thenReturn(new String[] {"STANDARD"});
        when(rowPartialName.isActive()).thenReturn(true);
        when(rowPartialName.getJobId()).thenReturn(UUID.randomUUID());
        when(rowPartialName.getAppointmentId()).thenReturn(UUID.randomUUID());
        when(rowPartialName.getStatus()).thenReturn("REQUESTED");
        when(rowPartialName.getStartTime()).thenReturn(Instant.parse("2026-10-28T04:00:00Z"));
        when(rowPartialName.getEndTime()).thenReturn(Instant.parse("2026-10-28T05:00:00Z"));
        when(rowPartialName.getFirstName()).thenReturn("Single");
        when(rowPartialName.getLastName()).thenReturn(null);
        when(rowPartialName.getPhone()).thenReturn("+84900000004");
        when(rowPartialName.getVehicleVin()).thenReturn("1HGCR2F83HA000001");
        when(rowPartialName.getServiceTypeName()).thenReturn("EXPRESS_SERVICE");

        ScheduleRow rowLastNameOnly = org.mockito.Mockito.mock(ScheduleRow.class);
        when(rowLastNameOnly.getBayId()).thenReturn(BAY_2);
        when(rowLastNameOnly.getBayName()).thenReturn("Bay 2");
        when(rowLastNameOnly.getBayTypes()).thenReturn(new String[] {"STANDARD"});
        when(rowLastNameOnly.isActive()).thenReturn(true);
        when(rowLastNameOnly.getJobId()).thenReturn(UUID.randomUUID());
        when(rowLastNameOnly.getAppointmentId()).thenReturn(UUID.randomUUID());
        when(rowLastNameOnly.getStatus()).thenReturn("REQUESTED");
        when(rowLastNameOnly.getStartTime()).thenReturn(Instant.parse("2026-10-28T06:00:00Z"));
        when(rowLastNameOnly.getEndTime()).thenReturn(Instant.parse("2026-10-28T07:00:00Z"));
        when(rowLastNameOnly.getFirstName()).thenReturn(null);
        when(rowLastNameOnly.getLastName()).thenReturn("Family");
        when(rowLastNameOnly.getPhone()).thenReturn("+84900000004");
        when(rowLastNameOnly.getVehicleVin()).thenReturn("1HGCR2F83HA000001");
        when(rowLastNameOnly.getServiceTypeName()).thenReturn("EXPRESS_SERVICE");

        when(scheduleQueryRepository.findDayRows(any(), any(), any()))
                .thenReturn(List.of(rowNullTypes, rowPartialName, rowLastNameOnly));

        ScheduleDayResponse response = scheduleService.findDaySchedule(DEALERSHIP_ID, DATE);

        assertThat(response.bays()).hasSize(2);
        assertThat(response.bays().get(0).bayTypes()).isEmpty();
        assertThat(response.bays().get(0).jobs().get(0).customerName()).isNull();
        assertThat(response.bays().get(1).jobs().get(0).customerName()).isEqualTo("Single");
        assertThat(response.bays().get(1).jobs().get(1).customerName()).isEqualTo("Family");
    }

    @Test
    void findDayScheduleThrowsBadRequestWhenDealershipMissing() {
        assertThatThrownBy(() -> scheduleService.findDaySchedule(null, DATE))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void findDayScheduleThrowsBadRequestWhenDateMissing() {
        assertThatThrownBy(() -> scheduleService.findDaySchedule(DEALERSHIP_ID, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void findHistoryMapsRowsToItems() {
        UUID appointmentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        HistoryRow row = mockHistoryRow(appointmentId);
        List<HistoryRow> rows = List.of(row);
        when(scheduleQueryRepository.findHistoryRows(
                        any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(rows);

        List<ScheduleHistoryItem> items = scheduleService.findHistory(
                new ScheduleHistoryQuery(DEALERSHIP_ID, "CANCELLED", null, null, null, null, null, null, 0, 50));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).id()).isEqualTo(appointmentId);
        assertThat(items.get(0).status()).isEqualTo("CANCELLED");
        assertThat(items.get(0).customerPhone()).isEqualTo("+84900000004");
    }

    @Test
    void findHistoryUsesLongOffsetForLargePageNumbers() {
        when(scheduleQueryRepository.findHistoryRows(
                        any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of());

        scheduleService.findHistory(
                new ScheduleHistoryQuery(DEALERSHIP_ID, null, null, null, null, null, null, null, 5_000, 200));

        ArgumentCaptor<Long> offset = ArgumentCaptor.forClass(Long.class);
        verify(scheduleQueryRepository)
                .findHistoryRows(any(), any(), any(), any(), any(), any(), any(), any(), offset.capture(), anyInt());
        assertThat(offset.getValue()).isEqualTo(1_000_000L);
    }

    @Test
    void findHistoryRejectsPageAboveTheSupportedRange() {
        assertThatThrownBy(() -> scheduleService.findHistory(new ScheduleHistoryQuery(
                        DEALERSHIP_ID, null, null, null, null, null, null, null, 2_147_483_647, 200)))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void findHistoryRejectsNegativePage() {
        assertThatThrownBy(() -> scheduleService.findHistory(
                        new ScheduleHistoryQuery(DEALERSHIP_ID, null, null, null, null, null, null, null, -1, 50)))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void findHistoryRejectsSizeOutsideTheSupportedRange() {
        assertThatThrownBy(() -> scheduleService.findHistory(
                        new ScheduleHistoryQuery(DEALERSHIP_ID, null, null, null, null, null, null, null, 0, 0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> scheduleService.findHistory(
                        new ScheduleHistoryQuery(DEALERSHIP_ID, null, null, null, null, null, null, null, 0, 201)))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    private static ScheduleRow row(UUID bayId, String bayName, UUID jobId, UUID appointmentId, String status) {
        ScheduleRow row = org.mockito.Mockito.mock(ScheduleRow.class);
        when(row.getBayId()).thenReturn(bayId);
        when(row.getBayName()).thenReturn(bayName);
        when(row.getBayTypes()).thenReturn(new String[] {"STANDARD"});
        when(row.isActive()).thenReturn(true);
        when(row.getJobId()).thenReturn(jobId);
        when(row.getAppointmentId()).thenReturn(appointmentId);
        when(row.getStatus()).thenReturn(status);
        when(row.getStartTime()).thenReturn(Instant.parse("2026-10-28T02:00:00Z"));
        when(row.getEndTime()).thenReturn(Instant.parse("2026-10-28T03:00:00Z"));
        when(row.getFirstName()).thenReturn("Demo");
        when(row.getLastName()).thenReturn("Customer");
        when(row.getPhone()).thenReturn("+84900000004");
        when(row.getVehicleVin()).thenReturn("1HGCR2F83HA000001");
        when(row.getServiceTypeName()).thenReturn("STANDARD_SERVICE");
        return row;
    }

    private static ScheduleRow emptyBayRow(UUID bayId, String bayName) {
        ScheduleRow row = org.mockito.Mockito.mock(ScheduleRow.class);
        when(row.getBayId()).thenReturn(bayId);
        when(row.getBayName()).thenReturn(bayName);
        when(row.getBayTypes()).thenReturn(new String[] {"STANDARD"});
        when(row.isActive()).thenReturn(true);
        return row;
    }

    private static HistoryRow mockHistoryRow(UUID appointmentId) {
        HistoryRow row = org.mockito.Mockito.mock(HistoryRow.class);
        when(row.getId()).thenReturn(appointmentId);
        when(row.getDealershipId()).thenReturn(DEALERSHIP_ID);
        when(row.getCustomerId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        when(row.getCustomerPhone()).thenReturn("+84900000004");
        when(row.getCustomerName()).thenReturn("Demo Customer");
        when(row.getVehicleVin()).thenReturn("1HGCR2F83HA000001");
        when(row.getStatus()).thenReturn("CANCELLED");
        when(row.getStartTime()).thenReturn(Instant.parse("2026-10-28T02:00:00Z"));
        when(row.getEndTime()).thenReturn(Instant.parse("2026-10-28T03:00:00Z"));
        return row;
    }
}
