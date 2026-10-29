package dev.hata.scheduler.appointment.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.hata.scheduler.appointment.dto.AppointmentResponse;
import dev.hata.scheduler.appointment.dto.CancelAppointmentResponse;
import dev.hata.scheduler.appointment.dto.CreateAppointmentRequest;
import dev.hata.scheduler.appointment.dto.JobNoteResponse;
import dev.hata.scheduler.appointment.dto.ServiceJobItemRequest;
import dev.hata.scheduler.appointment.dto.StaffAppointmentResponse;
import dev.hata.scheduler.appointment.model.Appointment;
import dev.hata.scheduler.appointment.model.AppointmentStatus;
import dev.hata.scheduler.appointment.model.JobStatus;
import dev.hata.scheduler.appointment.model.ServiceJob;
import dev.hata.scheduler.appointment.repository.AppointmentRepository;
import dev.hata.scheduler.appointment.repository.JobTechnicianAssignmentRepository;
import dev.hata.scheduler.appointment.repository.ServiceJobRepository;
import dev.hata.scheduler.appointment.repository.StaffAppointmentQueryRepository;
import dev.hata.scheduler.common.event.AppointmentSlotModifiedEvent;
import dev.hata.scheduler.common.event.NotificationDispatchEvent;
import dev.hata.scheduler.common.provider.DealershipValidationProvider;
import dev.hata.scheduler.common.provider.ServiceTypeMetadataProvider;
import dev.hata.scheduler.common.provider.ServiceTypeMetadataProvider.ServiceTypeMetadata;
import dev.hata.scheduler.common.provider.SlotCacheEvictionProvider;
import dev.hata.scheduler.common.provider.VehicleCategoryValidationProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Domain service managing customer appointment bookings, lifecycle state transitions,
 * multi-job validations, and slot cache evictions.
 */
@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final JobTechnicianAssignmentRepository jobTechnicianAssignmentRepository;
    private final ServiceJobRepository serviceJobRepository;
    private final StaffAppointmentQueryRepository staffAppointmentQueryRepository;
    private final DealershipValidationProvider dealershipValidation;
    private final VehicleCategoryValidationProvider categoryValidation;
    private final ServiceTypeMetadataProvider serviceTypeMetadataProvider;
    private final SlotCacheEvictionProvider slotCacheEviction;
    private final ApplicationEventPublisher eventPublisher;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            JobTechnicianAssignmentRepository jobTechnicianAssignmentRepository,
            ServiceJobRepository serviceJobRepository,
            StaffAppointmentQueryRepository staffAppointmentQueryRepository,
            DealershipValidationProvider dealershipValidation,
            VehicleCategoryValidationProvider categoryValidation,
            ServiceTypeMetadataProvider serviceTypeMetadataProvider,
            SlotCacheEvictionProvider slotCacheEviction,
            ApplicationEventPublisher eventPublisher) {
        this.appointmentRepository = appointmentRepository;
        this.jobTechnicianAssignmentRepository = jobTechnicianAssignmentRepository;
        this.serviceJobRepository = serviceJobRepository;
        this.staffAppointmentQueryRepository = staffAppointmentQueryRepository;
        this.dealershipValidation = dealershipValidation;
        this.categoryValidation = categoryValidation;
        this.serviceTypeMetadataProvider = serviceTypeMetadataProvider;
        this.slotCacheEviction = slotCacheEviction;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public JobNoteResponse readJobNotes(UUID jobId) {
        ServiceJob job = serviceJobRepository
                .findById(jobId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service job not found: " + jobId));
        return JobNoteResponse.of(jobId, job.getNotes());
    }

    /**
     * Retrieves the manager booking detail for an appointment inside the role scope.
     * Out-of-scope appointments return HTTP 404 like customer cross-access.
     */
    @Transactional(readOnly = true)
    public StaffAppointmentResponse findStaffAppointment(UUID id, UUID scopeDealershipId) {
        StaffAppointmentQueryRepository.StaffAppointmentRow row = staffAppointmentQueryRepository
                .findStaffAppointment(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found: " + id));

        if (scopeDealershipId != null && !scopeDealershipId.equals(row.getDealershipId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found: " + id);
        }

        List<StaffAppointmentResponse.StaffJobView> jobs = staffAppointmentQueryRepository.findStaffJobs(id).stream()
                .map(job -> new StaffAppointmentResponse.StaffJobView(
                        job.getJobId(),
                        job.getServiceName(),
                        job.getBayName(),
                        job.getStatus(),
                        job.getStartTime(),
                        job.getEndTime(),
                        splitNotes(job.getNotes()),
                        job.getEstimateCost(),
                        job.getFinalCost()))
                .toList();

        List<StaffAppointmentResponse.StaffCommunicationView> communications =
                staffAppointmentQueryRepository.findStaffCommunications(id).stream()
                        .map(log -> new StaffAppointmentResponse.StaffCommunicationView(
                                log.getEventType(), log.getChannel(), log.getDeliveryStatus(), log.getDispatchedAt()))
                        .toList();

        StaffAppointmentResponse.StaffView staffView = new StaffAppointmentResponse.StaffView(
                row.getCustomerId().toString(),
                row.getCustomerName(),
                row.getCustomerPhone(),
                row.getVehicleVin(),
                row.getVehicleCategory(),
                jobs,
                communications);

        return new StaffAppointmentResponse(
                row.getId(),
                row.getDealershipId(),
                row.getStatus(),
                row.getStartTime(),
                row.getEndTime(),
                row.getEstimateCost(),
                row.getTotalCost(),
                row.getCreatedAt(),
                staffView);
    }

    private static List<String> splitNotes(String notes) {
        return JobNoteResponse.of(new UUID(0L, 0L), notes).notes();
    }

    /**
     * Result of a technician or manager inspection note append.
     */
    public record JobNoteResult(UUID jobId, String appended) {}

    /**
     * Books a customer appointment with itemized service jobs and transactional outbox event.
     */
    @Transactional
    public AppointmentResponse book(
            CreateAppointmentRequest request, UUID customerId, String customerPhone, UUID idempotencyKey) {
        Optional<Appointment> existing = appointmentRepository.findWithJobsByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Appointment appt = existing.get();
            if (!appt.getCustomerId().equals(customerId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Idempotency key already used by another customer");
            }
            return AppointmentResponse.from(appt);
        }

        UUID dealershipId = request.dealershipId();
        UUID categoryId = request.vehicleCategoryId();

        if (!dealershipValidation.existsActiveDealership(dealershipId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealership not found: " + dealershipId);
        }
        if (!categoryValidation.existsCategoryInDealership(dealershipId, categoryId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vehicle category " + categoryId + " not found for dealership " + dealershipId);
        }

        List<ServiceJobItemRequest> jobs = request.serviceJobs();
        if (jobs == null || jobs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one service job must be specified");
        }

        // Sort jobs chronologically by start time
        List<ServiceJobItemRequest> sortedJobs = jobs.stream()
                .sorted(Comparator.comparing(ServiceJobItemRequest::startTime))
                .toList();

        // 1. Intra-appointment non-overlap validation
        for (int i = 0; i < sortedJobs.size(); i++) {
            ServiceJobItemRequest job = sortedJobs.get(i);
            if (!job.endTime().isAfter(job.startTime())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Service job end time must be after start time");
            }
            if (i < sortedJobs.size() - 1) {
                ServiceJobItemRequest nextJob = sortedJobs.get(i + 1);
                if (job.endTime().isAfter(nextJob.startTime())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Service job time slots for vehicle cannot overlap");
                }
            }
        }

        // 2. Validate bays and duration bounds against service type metadata
        BigDecimal totalCost = BigDecimal.ZERO;
        Instant now = Instant.now();

        Appointment appointment = new Appointment();
        UUID appointmentId = UUID.randomUUID();
        appointment.setId(appointmentId);
        appointment.setDealershipId(dealershipId);
        appointment.setCustomerId(customerId);
        appointment.setVehicleCategoryId(categoryId);
        appointment.setVehicleVin(request.vehicleVin());
        appointment.setStartTime(sortedJobs.get(0).startTime());
        appointment.setEndTime(sortedJobs.get(sortedJobs.size() - 1).endTime());
        appointment.setIdempotencyKey(idempotencyKey);
        appointment.setCreatedAt(now);
        appointment.setUpdatedAt(now);
        appointment.setCreatedBy(customerId);
        appointment.setUpdatedBy(customerId);

        for (ServiceJobItemRequest jobReq : sortedJobs) {
            if (!dealershipValidation.existsActiveBay(dealershipId, jobReq.bayId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Workshop bay not found or inactive: " + jobReq.bayId());
            }

            ServiceTypeMetadata metadata = serviceTypeMetadataProvider
                    .findServiceTypeMetadata(dealershipId, categoryId, jobReq.serviceTypeId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Service type " + jobReq.serviceTypeId() + " not configured for dealership and category"));

            Duration actualDuration = Duration.between(jobReq.startTime(), jobReq.endTime());
            if (actualDuration.compareTo(Duration.ofMinutes(metadata.estimatedMinutes())) > 0) {
                long durationMin = actualDuration.toMinutes();
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Service job duration " + durationMin + "m exceeds base duration " + metadata.estimatedMinutes()
                                + "m for service type " + jobReq.serviceTypeId());
            }

            ServiceJob jobEntity = new ServiceJob();
            jobEntity.setId(UUID.randomUUID());
            jobEntity.setAppointment(appointment);
            jobEntity.setServiceTypeId(jobReq.serviceTypeId());
            jobEntity.setBayId(jobReq.bayId());
            jobEntity.setStartTime(jobReq.startTime());
            jobEntity.setEndTime(jobReq.endTime());
            jobEntity.setStatus(JobStatus.PENDING);
            jobEntity.setBaseMinutes(metadata.estimatedMinutes());
            jobEntity.setEstimateCost(metadata.cost());
            jobEntity.setCreatedAt(now);
            jobEntity.setUpdatedAt(now);
            jobEntity.setCreatedBy(customerId);
            jobEntity.setUpdatedBy(customerId);

            appointment.addServiceJob(jobEntity);
            totalCost = totalCost.add(metadata.cost());
        }

        appointment.setEstimateCost(totalCost);

        // Auto-confirm check
        boolean autoConfirm = dealershipValidation.isAutoConfirmEnabled(dealershipId);
        AppointmentStatus initialStatus = autoConfirm ? AppointmentStatus.CONFIRMED : AppointmentStatus.REQUESTED;
        appointment.setStatus(initialStatus);

        Set<LocalDate> appointmentDates = extractAppointmentDates(dealershipId, sortedJobs);

        Appointment saved;
        try {
            saved = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            // SYSTEM_DESIGN §12.2: Immediately evict cached slot grids for that date on collision
            for (LocalDate date : appointmentDates) {
                slotCacheEviction.evictSlots(dealershipId, date);
            }
            throw ex;
        }

        // Publish notification event
        String eventType =
                (initialStatus == AppointmentStatus.CONFIRMED) ? "appointment.confirmed" : "appointment.requested";
        String payload = "{\"id\":\"" + saved.getId() + "\",\"appointmentId\":\"" + saved.getId() + "\",\"status\":\""
                + initialStatus + "\",\"dealershipId\":\"" + dealershipId + "\"}";

        eventPublisher.publishEvent(new NotificationDispatchEvent(
                dealershipId,
                saved.getId(),
                customerId,
                customerPhone,
                eventType,
                "APPOINTMENT",
                saved.getId(),
                payload));

        // Declarative event-driven eviction after transaction commits (SYSTEM_DESIGN §12.1)
        eventPublisher.publishEvent(new AppointmentSlotModifiedEvent(dealershipId, appointmentDates));

        return AppointmentResponse.from(saved);
    }

    /**
     * Lists appointments owned by the customer sorted by start time descending.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> findCustomerAppointments(UUID customerId) {
        return appointmentRepository.findByCustomerIdOrderByStartTimeDesc(customerId).stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    /**
     * Retrieves appointment details by id for the authenticated customer.
     */
    @Transactional(readOnly = true)
    public AppointmentResponse findAppointmentById(UUID id, UUID customerId) {
        return appointmentRepository
                .findWithJobsByIdAndCustomerId(id, customerId)
                .map(AppointmentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found: " + id));
    }

    /**
     * Cancels an appointment before check-in (from REQUESTED or CONFIRMED only).
     */
    @Transactional
    public CancelAppointmentResponse cancelAppointment(UUID id, UUID customerId, String customerPhone) {
        Appointment appointment = appointmentRepository
                .findWithJobsByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found: " + id));

        AppointmentStatus current = appointment.getStatus();
        if (current == AppointmentStatus.CANCELLED
                || current == AppointmentStatus.REJECTED
                || current == AppointmentStatus.NO_SHOW) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Appointment is already in terminal state " + current);
        }

        if (current != AppointmentStatus.REQUESTED && current != AppointmentStatus.CONFIRMED) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cannot cancel appointment in status " + current
                            + ". Cancellation permitted prior to check-in only.");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setUpdatedAt(Instant.now());
        appointment.setUpdatedBy(customerId);
        if (appointment.getServiceJobs() != null) {
            List<UUID> jobIds = new ArrayList<>();
            for (ServiceJob job : appointment.getServiceJobs()) {
                job.setStatus(JobStatus.CANCELLED);
                job.setUpdatedAt(Instant.now());
                job.setUpdatedBy(customerId);
                jobIds.add(job.getId());
            }
            if (!jobIds.isEmpty()) {
                jobTechnicianAssignmentRepository.deleteByJobIdIn(jobIds);
            }
        }

        Appointment saved = appointmentRepository.saveAndFlush(appointment);

        // Publish cancellation event
        String payload = "{\"id\":\"" + saved.getId() + "\",\"appointmentId\":\"" + saved.getId()
                + "\",\"status\":\"CANCELLED\"}";
        eventPublisher.publishEvent(new NotificationDispatchEvent(
                saved.getDealershipId(),
                saved.getId(),
                customerId,
                customerPhone,
                "appointment.cancelled",
                "APPOINTMENT",
                saved.getId(),
                payload));

        // Declarative event-driven eviction after transaction commits (SYSTEM_DESIGN §12.1).
        // Per-job dates on top of appointment-level dates so intermediate job spans
        // (multi-day bookings with jobs on non-terminal dates) evict every touched grid.
        Set<LocalDate> dates = extractAppointmentDates(saved.getDealershipId(), saved);
        if (!saved.getServiceJobs().isEmpty()) {
            dates.addAll(extractJobDates(saved.getDealershipId(), saved.getServiceJobs()));
        }
        eventPublisher.publishEvent(new AppointmentSlotModifiedEvent(saved.getDealershipId(), dates));

        return new CancelAppointmentResponse(saved.getId(), AppointmentStatus.CANCELLED, Instant.now());
    }

    /**
     * Appends an inspection note to a service job. Only the assigned technician or a manager may
     * append notes. Notes surface as feedback data in the staff booking detail view.
     */
    @Transactional
    public JobNoteResult appendJobNote(UUID jobId, UUID callerId, String note) {
        return appendJobNote(jobId, callerId, note, false, null);
    }

    /**
     * Appends an inspection note to a service job when the caller is permitted.
     * Manager overrides the technician assignment check when {@code isManager} is true.
     */
    @Transactional
    public JobNoteResult appendJobNote(UUID jobId, UUID callerId, String note, boolean isManager) {
        return appendJobNote(jobId, callerId, note, isManager, null);
    }

    /**
     * Appends an inspection note to a service job when the caller is permitted.
     * Enforces dealership scope for manager callers to prevent cross-dealership modifications.
     * Performs an atomic native update to prevent concurrent note appends from dropping notes.
     */
    @Transactional
    public JobNoteResult appendJobNote(
            UUID jobId, UUID callerId, String note, boolean isManager, UUID managerDealershipScope) {
        ServiceJob job = serviceJobRepository
                .findById(jobId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service job not found: " + jobId));

        if (isManager
                && managerDealershipScope != null
                && !managerDealershipScope.equals(job.getAppointment().getDealershipId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service job not found: " + jobId);
        }

        boolean assigned = jobTechnicianAssignmentRepository.findByJobId(jobId).stream()
                .anyMatch(a -> a.getTechnicianId().equals(callerId));

        if (!isManager && !assigned) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only the assigned technician may append notes to job " + jobId);
        }

        serviceJobRepository.appendNoteAtomic(jobId, note);

        return new JobNoteResult(jobId, note);
    }

    private Set<LocalDate> extractAppointmentDates(UUID dealershipId, List<ServiceJobItemRequest> jobs) {
        String timezone = dealershipValidation.getDealershipTimezone(dealershipId);
        ZoneId zone = ZoneId.of(timezone);
        Set<LocalDate> dates = new HashSet<>();
        for (ServiceJobItemRequest job : jobs) {
            dates.add(job.startTime().atZone(zone).toLocalDate());
            dates.add(job.endTime().atZone(zone).toLocalDate());
        }
        return dates;
    }

    private Set<LocalDate> extractAppointmentDates(UUID dealershipId, Appointment appointment) {
        String timezone = dealershipValidation.getDealershipTimezone(dealershipId);
        ZoneId zone = ZoneId.of(timezone);
        Set<LocalDate> dates = new HashSet<>();
        if (appointment.getStartTime() != null) {
            dates.add(appointment.getStartTime().atZone(zone).toLocalDate());
        }
        if (appointment.getEndTime() != null) {
            dates.add(appointment.getEndTime().atZone(zone).toLocalDate());
        }
        return dates;
    }

    private Set<LocalDate> extractJobDates(UUID dealershipId, List<ServiceJob> jobs) {
        String timezone = dealershipValidation.getDealershipTimezone(dealershipId);
        ZoneId zone = ZoneId.of(timezone);
        Set<LocalDate> dates = new HashSet<>();
        for (ServiceJob job : jobs) {
            if (job.getStartTime() != null) {
                dates.add(job.getStartTime().atZone(zone).toLocalDate());
            }
            if (job.getEndTime() != null) {
                dates.add(job.getEndTime().atZone(zone).toLocalDate());
            }
        }
        return dates;
    }
}
