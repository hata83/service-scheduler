package dev.hata.scheduler.scheduling.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipValidationProvider;
import dev.hata.scheduler.scheduling.dto.ScheduleBayColumn;
import dev.hata.scheduler.scheduling.dto.ScheduleDayResponse;
import dev.hata.scheduler.scheduling.dto.ScheduleHistoryItem;
import dev.hata.scheduler.scheduling.dto.ScheduleHistoryQuery;
import dev.hata.scheduler.scheduling.dto.ScheduleJobBlock;
import dev.hata.scheduler.scheduling.repository.ScheduleQueryRepository;
import dev.hata.scheduler.scheduling.repository.ScheduleQueryRepository.HistoryRow;
import dev.hata.scheduler.scheduling.repository.ScheduleQueryRepository.ScheduleRow;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Domain service for the manager schedule board: day grid view and filtered history list
 * (change manager-schedule-realtime, SYSTEM_DESIGN section 3.3).
 */
@Service
public class ScheduleService {

    private static final int MAX_HISTORY_ROWS = 200;

    private final ScheduleQueryRepository scheduleQueryRepository;
    private final DealershipValidationProvider dealershipValidation;

    public ScheduleService(
            ScheduleQueryRepository scheduleQueryRepository, DealershipValidationProvider dealershipValidation) {
        this.scheduleQueryRepository = scheduleQueryRepository;
        this.dealershipValidation = dealershipValidation;
    }

    /**
     * Returns the configured timezone of a dealership.
     */
    @Transactional(readOnly = true)
    public String dealershipTimezone(UUID dealershipId) {
        return dealershipValidation.getDealershipTimezone(dealershipId);
    }

    /**
     * Returns the full day schedule of one dealership with one column per active bay.
     */
    @Transactional(readOnly = true)
    public ScheduleDayResponse findDaySchedule(UUID dealershipId, LocalDate date) {
        if (dealershipId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dealershipId is required for the day schedule");
        }
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required for the day schedule");
        }

        ZoneId zone = ZoneId.of(dealershipValidation.getDealershipTimezone(dealershipId));
        Instant startOfDay = date.atStartOfDay(zone).toInstant();
        Instant endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant();

        List<ScheduleRow> rows = scheduleQueryRepository.findDayRows(dealershipId, startOfDay, endOfDay);

        Map<UUID, List<ScheduleJobBlock>> jobsByBay = new LinkedHashMap<>();
        Map<UUID, String> bayNames = new LinkedHashMap<>();
        Map<UUID, List<String>> bayTypes = new LinkedHashMap<>();
        Map<UUID, Boolean> bayActive = new LinkedHashMap<>();

        for (ScheduleRow row : rows) {
            bayNames.putIfAbsent(row.getBayId(), row.getBayName());
            bayTypes.putIfAbsent(row.getBayId(), toBayTypes(row.getBayTypes()));
            bayActive.putIfAbsent(row.getBayId(), row.isActive());
            jobsByBay.computeIfAbsent(row.getBayId(), key -> new ArrayList<>());
            if (row.getJobId() == null) {
                continue;
            }
            jobsByBay
                    .get(row.getBayId())
                    .add(new ScheduleJobBlock(
                            row.getJobId(),
                            row.getAppointmentId(),
                            row.getStatus(),
                            row.getStartTime(),
                            row.getEndTime(),
                            customerName(row.getFirstName(), row.getLastName()),
                            row.getPhone(),
                            row.getVehicleVin(),
                            row.getServiceTypeName()));
        }

        List<ScheduleBayColumn> bays = new ArrayList<>();
        for (Map.Entry<UUID, List<ScheduleJobBlock>> entry : jobsByBay.entrySet()) {
            bays.add(new ScheduleBayColumn(
                    entry.getKey(),
                    bayNames.get(entry.getKey()),
                    bayTypes.get(entry.getKey()),
                    Boolean.TRUE.equals(bayActive.get(entry.getKey())),
                    entry.getValue()));
        }

        return new ScheduleDayResponse(dealershipId, date, bays);
    }

    /**
     * Returns the filtered appointment history for the manager history list.
     * The result is capped so one wide query cannot load the whole table.
     */
    @Transactional(readOnly = true)
    public List<ScheduleHistoryItem> findHistory(ScheduleHistoryQuery query) {
        List<HistoryRow> rows = scheduleQueryRepository.findHistoryRows(
                query.dealershipId(),
                query.status(),
                query.from(),
                query.to(),
                query.bayId(),
                query.customerPhone(),
                MAX_HISTORY_ROWS);

        return rows.stream()
                .map(row -> new ScheduleHistoryItem(
                        row.getId(),
                        row.getDealershipId(),
                        row.getCustomerId(),
                        row.getCustomerPhone(),
                        row.getCustomerName(),
                        row.getVehicleVin(),
                        row.getStatus(),
                        row.getStartTime(),
                        row.getEndTime(),
                        row.getEstimateCost(),
                        row.getTotalCost()))
                .toList();
    }

    private static List<String> toBayTypes(String... bayTypes) {
        if (bayTypes == null) {
            return List.of();
        }
        return List.of(bayTypes);
    }

    private static String customerName(String firstName, String lastName) {
        if (firstName == null && lastName == null) {
            return null;
        }
        return (firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName);
    }
}
