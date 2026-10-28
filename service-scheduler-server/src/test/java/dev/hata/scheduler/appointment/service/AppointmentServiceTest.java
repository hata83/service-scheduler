package dev.hata.scheduler.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import dev.hata.scheduler.appointment.model.JobTechnicianAssignment;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for the AppointmentService booking, cancellation, and staff note flows.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID TECHNICIAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final String CUSTOMER_PHONE = "+84900000004";
    private static final String VIN = "1HGBH41JXMN109186";

    private static final UUID SERVICE_TYPE_1 = UUID.fromString("22222222-0000-0000-0000-000000000001");
    private static final UUID SERVICE_TYPE_2 = UUID.fromString("22222222-0000-0000-0000-000000000002");
    private static final UUID BAY_1 = UUID.fromString("33333333-0000-0000-0000-000000000001");
    private static final UUID BAY_2 = UUID.fromString("33333333-0000-0000-0000-000000000002");

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private JobTechnicianAssignmentRepository jobTechnicianAssignmentRepository;

    @Mock
    private ServiceJobRepository serviceJobRepository;

    @Mock
    private StaffAppointmentQueryRepository staffAppointmentQueryRepository;

    @Mock
    private DealershipValidationProvider dealershipValidation;

    @Mock
    private VehicleCategoryValidationProvider categoryValidation;

    @Mock
    private ServiceTypeMetadataProvider serviceTypeMetadataProvider;

    @Mock
    private SlotCacheEvictionProvider slotCacheEviction;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(
                appointmentRepository,
                jobTechnicianAssignmentRepository,
                serviceJobRepository,
                staffAppointmentQueryRepository,
                dealershipValidation,
                categoryValidation,
                serviceTypeMetadataProvider,
                slotCacheEviction,
                eventPublisher);
    }

    @Test
    void bookSucceedsWithTwoConsecutiveServiceJobsAndAutoConfirmTrue() {
        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(categoryValidation.existsCategoryInDealership(DEALERSHIP_ID, CATEGORY_ID))
                .thenReturn(true);
        when(dealershipValidation.existsActiveBay(DEALERSHIP_ID, BAY_1)).thenReturn(true);
        when(dealershipValidation.existsActiveBay(DEALERSHIP_ID, BAY_2)).thenReturn(true);
        when(dealershipValidation.isAutoConfirmEnabled(DEALERSHIP_ID)).thenReturn(true);
        when(dealershipValidation.getDealershipTimezone(DEALERSHIP_ID)).thenReturn("Asia/Ho_Chi_Minh");

        when(serviceTypeMetadataProvider.findServiceTypeMetadata(DEALERSHIP_ID, CATEGORY_ID, SERVICE_TYPE_1))
                .thenReturn(Optional.of(new ServiceTypeMetadata(SERVICE_TYPE_1, 30, new BigDecimal("60.00"))));
        when(serviceTypeMetadataProvider.findServiceTypeMetadata(DEALERSHIP_ID, CATEGORY_ID, SERVICE_TYPE_2))
                .thenReturn(Optional.of(new ServiceTypeMetadata(SERVICE_TYPE_2, 60, new BigDecimal("150.00"))));

        Instant start = Instant.parse("2026-10-28T01:00:00Z");
        Instant mid = start.plus(Duration.ofMinutes(30));
        Instant end = mid.plus(Duration.ofMinutes(60));

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                CATEGORY_ID,
                VIN,
                List.of(
                        new ServiceJobItemRequest(SERVICE_TYPE_1, BAY_1, start, mid),
                        new ServiceJobItemRequest(SERVICE_TYPE_2, BAY_2, mid, end)));

        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentResponse response = appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, UUID.randomUUID());

        assertThat(response.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(response.estimateCost()).isEqualByComparingTo("210.00");
        assertThat(response.serviceJobs()).hasSize(2);
        assertThat(response.startTime()).isEqualTo(start);
        assertThat(response.endTime()).isEqualTo(end);

        verify(eventPublisher).publishEvent(any(AppointmentSlotModifiedEvent.class));
        verify(eventPublisher).publishEvent(any(NotificationDispatchEvent.class));
    }

    @Test
    void bookSucceedsWithAutoConfirmFalseSetsRequestedStatus() {
        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(categoryValidation.existsCategoryInDealership(DEALERSHIP_ID, CATEGORY_ID))
                .thenReturn(true);
        when(dealershipValidation.existsActiveBay(DEALERSHIP_ID, BAY_1)).thenReturn(true);
        when(dealershipValidation.isAutoConfirmEnabled(DEALERSHIP_ID)).thenReturn(false);
        when(dealershipValidation.getDealershipTimezone(DEALERSHIP_ID)).thenReturn("Asia/Ho_Chi_Minh");

        when(serviceTypeMetadataProvider.findServiceTypeMetadata(DEALERSHIP_ID, CATEGORY_ID, SERVICE_TYPE_1))
                .thenReturn(Optional.of(new ServiceTypeMetadata(SERVICE_TYPE_1, 30, new BigDecimal("60.00"))));

        Instant start = Instant.parse("2026-10-28T01:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(30));

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID, CATEGORY_ID, VIN, List.of(new ServiceJobItemRequest(SERVICE_TYPE_1, BAY_1, start, end)));

        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentResponse response = appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, UUID.randomUUID());

        assertThat(response.status()).isEqualTo(AppointmentStatus.REQUESTED);
    }

    @Test
    void bookImmediatelyEvictsSlotCacheWhenDataIntegrityViolationOccurs() {
        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(categoryValidation.existsCategoryInDealership(DEALERSHIP_ID, CATEGORY_ID))
                .thenReturn(true);
        when(dealershipValidation.existsActiveBay(DEALERSHIP_ID, BAY_1)).thenReturn(true);
        when(dealershipValidation.isAutoConfirmEnabled(DEALERSHIP_ID)).thenReturn(true);
        when(dealershipValidation.getDealershipTimezone(DEALERSHIP_ID)).thenReturn("Asia/Ho_Chi_Minh");
        when(serviceTypeMetadataProvider.findServiceTypeMetadata(DEALERSHIP_ID, CATEGORY_ID, SERVICE_TYPE_1))
                .thenReturn(Optional.of(new ServiceTypeMetadata(SERVICE_TYPE_1, 30, new BigDecimal("60.00"))));

        Instant start = Instant.parse("2026-10-28T01:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(30));

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID, CATEGORY_ID, VIN, List.of(new ServiceJobItemRequest(SERVICE_TYPE_1, BAY_1, start, end)));

        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenThrow(new DataIntegrityViolationException("overlap"));

        assertThatThrownBy(() -> appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(slotCacheEviction)
                .evictSlots(
                        DEALERSHIP_ID,
                        start.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate());
    }

    @Test
    void bookThrowsNotFoundWhenDealershipMissing() {
        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(false);

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                CATEGORY_ID,
                VIN,
                List.of(new ServiceJobItemRequest(
                        SERVICE_TYPE_1, BAY_1, Instant.now(), Instant.now().plus(Duration.ofMinutes(30)))));

        assertThatThrownBy(() -> appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void bookThrowsBadRequestWhenCategoryMissing() {
        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(categoryValidation.existsCategoryInDealership(DEALERSHIP_ID, CATEGORY_ID))
                .thenReturn(false);

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                CATEGORY_ID,
                VIN,
                List.of(new ServiceJobItemRequest(
                        SERVICE_TYPE_1, BAY_1, Instant.now(), Instant.now().plus(Duration.ofMinutes(30)))));

        assertThatThrownBy(() -> appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void bookThrowsBadRequestWhenJobsEmpty() {
        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(categoryValidation.existsCategoryInDealership(DEALERSHIP_ID, CATEGORY_ID))
                .thenReturn(true);

        CreateAppointmentRequest request = new CreateAppointmentRequest(DEALERSHIP_ID, CATEGORY_ID, VIN, List.of());

        assertThatThrownBy(() -> appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void bookThrowsBadRequestWhenJobEndTimeBeforeStartTime() {
        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(categoryValidation.existsCategoryInDealership(DEALERSHIP_ID, CATEGORY_ID))
                .thenReturn(true);

        Instant start = Instant.now();
        Instant invalidEnd = start.minus(Duration.ofMinutes(10));

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                CATEGORY_ID,
                VIN,
                List.of(new ServiceJobItemRequest(SERVICE_TYPE_1, BAY_1, start, invalidEnd)));

        assertThatThrownBy(() -> appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
                .hasMessageContaining("end time must be after start time");
    }

    @Test
    void bookThrowsBadRequestWhenBayMissing() {
        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(categoryValidation.existsCategoryInDealership(DEALERSHIP_ID, CATEGORY_ID))
                .thenReturn(true);
        when(dealershipValidation.existsActiveBay(DEALERSHIP_ID, BAY_1)).thenReturn(false);

        Instant start = Instant.now();
        Instant end = start.plus(Duration.ofMinutes(30));

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID, CATEGORY_ID, VIN, List.of(new ServiceJobItemRequest(SERVICE_TYPE_1, BAY_1, start, end)));

        assertThatThrownBy(() -> appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
                .hasMessageContaining("Workshop bay not found");
    }

    @Test
    void bookThrowsBadRequestWhenServiceTypeNotConfigured() {
        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(categoryValidation.existsCategoryInDealership(DEALERSHIP_ID, CATEGORY_ID))
                .thenReturn(true);
        when(dealershipValidation.existsActiveBay(DEALERSHIP_ID, BAY_1)).thenReturn(true);
        when(serviceTypeMetadataProvider.findServiceTypeMetadata(DEALERSHIP_ID, CATEGORY_ID, SERVICE_TYPE_1))
                .thenReturn(Optional.empty());

        Instant start = Instant.now();
        Instant end = start.plus(Duration.ofMinutes(30));

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID, CATEGORY_ID, VIN, List.of(new ServiceJobItemRequest(SERVICE_TYPE_1, BAY_1, start, end)));

        assertThatThrownBy(() -> appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
                .hasMessageContaining("not configured");
    }

    @Test
    void bookThrowsRequestEntityConflictOnIdempotencyKeyReplayByOtherCustomer() {
        UUID idempotencyKey = UUID.randomUUID();
        Appointment existing = new Appointment();
        existing.setId(UUID.randomUUID());
        existing.setCustomerId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        existing.setDealershipId(DEALERSHIP_ID);
        existing.setStartTime(Instant.now());
        existing.setEndTime(Instant.now().plus(Duration.ofMinutes(30)));

        when(appointmentRepository.findWithJobsByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                CATEGORY_ID,
                VIN,
                List.of(new ServiceJobItemRequest(
                        SERVICE_TYPE_1, BAY_1, Instant.now(), Instant.now().plus(Duration.ofMinutes(30)))));

        assertThatThrownBy(() -> appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, idempotencyKey))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    }

    @Test
    void bookReturnsCachedResponseOnIdempotencyKeyReplayBySameCustomer() {
        UUID idempotencyKey = UUID.randomUUID();
        Appointment existing = new Appointment();
        existing.setId(UUID.randomUUID());
        existing.setCustomerId(CUSTOMER_ID);
        existing.setDealershipId(DEALERSHIP_ID);
        existing.setVehicleCategoryId(CATEGORY_ID);
        existing.setVehicleVin(VIN);
        existing.setStartTime(Instant.parse("2026-10-28T01:00:00Z"));
        existing.setEndTime(Instant.parse("2026-10-28T01:30:00Z"));
        existing.setStatus(AppointmentStatus.CONFIRMED);
        existing.setEstimateCost(new BigDecimal("60.00"));
        existing.setCreatedAt(existing.getStartTime());
        existing.setServiceJobs(List.of());

        when(appointmentRepository.findWithJobsByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                CATEGORY_ID,
                VIN,
                List.of(new ServiceJobItemRequest(
                        SERVICE_TYPE_1, BAY_1, Instant.now(), Instant.now().plus(Duration.ofMinutes(30)))));

        AppointmentResponse response = appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, idempotencyKey);

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.status()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void cancelAppointmentThrowsBadRequestWhenAlreadyCancelled() {
        UUID id = UUID.randomUUID();
        Appointment a = new Appointment();
        a.setId(id);
        a.setDealershipId(DEALERSHIP_ID);
        a.setCustomerId(CUSTOMER_ID);
        a.setStatus(AppointmentStatus.CANCELLED);

        when(appointmentRepository.findWithJobsByIdAndCustomerId(id, CUSTOMER_ID))
                .thenReturn(Optional.of(a));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(id, CUSTOMER_ID, CUSTOMER_PHONE))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
                .hasMessageContaining("terminal state");
    }

    @Test
    void cancelAppointmentSucceedsForRequestedStatus() {
        UUID id = UUID.randomUUID();
        Appointment a = new Appointment();
        a.setId(id);
        a.setDealershipId(DEALERSHIP_ID);
        a.setCustomerId(CUSTOMER_ID);
        a.setStatus(AppointmentStatus.REQUESTED);
        a.setStartTime(Instant.parse("2026-10-28T01:00:00Z"));
        a.setEndTime(Instant.parse("2026-10-28T02:00:00Z"));

        when(appointmentRepository.findWithJobsByIdAndCustomerId(id, CUSTOMER_ID))
                .thenReturn(Optional.of(a));
        when(dealershipValidation.getDealershipTimezone(DEALERSHIP_ID)).thenReturn("Asia/Ho_Chi_Minh");
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        CancelAppointmentResponse response = appointmentService.cancelAppointment(id, CUSTOMER_ID, CUSTOMER_PHONE);

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancelAppointmentThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(appointmentRepository.findWithJobsByIdAndCustomerId(id, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.cancelAppointment(id, CUSTOMER_ID, CUSTOMER_PHONE))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void cancelAppointmentRejectsCheckedInStatus() {
        UUID id = UUID.randomUUID();
        Appointment a = new Appointment();
        a.setId(id);
        a.setDealershipId(DEALERSHIP_ID);
        a.setCustomerId(CUSTOMER_ID);
        a.setStatus(AppointmentStatus.CHECKED_IN);

        when(appointmentRepository.findWithJobsByIdAndCustomerId(id, CUSTOMER_ID))
                .thenReturn(Optional.of(a));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(id, CUSTOMER_ID, CUSTOMER_PHONE))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void findCustomerAppointmentsReturnsList() {
        Appointment a = new Appointment();
        a.setId(UUID.randomUUID());
        a.setCustomerId(CUSTOMER_ID);
        a.setDealershipId(DEALERSHIP_ID);
        a.setVehicleCategoryId(CATEGORY_ID);
        a.setVehicleVin(VIN);
        a.setStartTime(Instant.parse("2026-10-28T01:00:00Z"));
        a.setEndTime(Instant.parse("2026-10-28T02:00:00Z"));
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setCreatedAt(Instant.parse("2026-10-27T10:00:00Z"));
        a.setServiceJobs(List.of());

        when(appointmentRepository.findByCustomerIdOrderByStartTimeDesc(CUSTOMER_ID))
                .thenReturn(List.of(a));

        List<AppointmentResponse> result = appointmentService.findCustomerAppointments(CUSTOMER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(a.getId());
    }

    @Test
    void findAppointmentByIdReturnsAppointment() {
        UUID id = UUID.randomUUID();
        Appointment a = new Appointment();
        a.setId(id);
        a.setCustomerId(CUSTOMER_ID);
        a.setDealershipId(DEALERSHIP_ID);
        a.setVehicleCategoryId(CATEGORY_ID);
        a.setVehicleVin(VIN);
        a.setStartTime(Instant.parse("2026-10-28T01:00:00Z"));
        a.setEndTime(Instant.parse("2026-10-28T02:00:00Z"));
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setCreatedAt(Instant.parse("2026-10-27T10:00:00Z"));
        a.setServiceJobs(List.of());

        when(appointmentRepository.findWithJobsByIdAndCustomerId(id, CUSTOMER_ID))
                .thenReturn(Optional.of(a));

        AppointmentResponse result = appointmentService.findAppointmentById(id, CUSTOMER_ID);

        assertThat(result.id()).isEqualTo(id);
    }

    @Test
    void findAppointmentByIdThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(appointmentRepository.findWithJobsByIdAndCustomerId(id, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.findAppointmentById(id, CUSTOMER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelAppointmentThrowsBadRequestWhenRejectedOrNoShow() {
        UUID id = UUID.randomUUID();
        Appointment a = new Appointment();
        a.setId(id);
        a.setDealershipId(DEALERSHIP_ID);
        a.setCustomerId(CUSTOMER_ID);
        a.setStatus(AppointmentStatus.REJECTED);

        when(appointmentRepository.findWithJobsByIdAndCustomerId(id, CUSTOMER_ID))
                .thenReturn(Optional.of(a));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(id, CUSTOMER_ID, CUSTOMER_PHONE))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

        a.setStatus(AppointmentStatus.NO_SHOW);
        assertThatThrownBy(() -> appointmentService.cancelAppointment(id, CUSTOMER_ID, CUSTOMER_PHONE))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancelAppointmentWithServiceJobsCancelsJobsAndEvictsPerJobDates() {
        UUID id = UUID.randomUUID();
        Appointment a = new Appointment();
        a.setId(id);
        a.setDealershipId(DEALERSHIP_ID);
        a.setCustomerId(CUSTOMER_ID);
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setStartTime(Instant.parse("2026-10-28T01:00:00Z"));
        a.setEndTime(Instant.parse("2026-10-28T03:00:00Z"));

        ServiceJob job1 = new ServiceJob();
        job1.setId(UUID.randomUUID());
        job1.setStatus(JobStatus.PENDING);
        job1.setStartTime(Instant.parse("2026-10-28T01:00:00Z"));
        job1.setEndTime(Instant.parse("2026-10-28T02:00:00Z"));

        ServiceJob job2 = new ServiceJob();
        job2.setId(UUID.randomUUID());
        job2.setStatus(JobStatus.PENDING);
        job2.setStartTime(Instant.parse("2026-10-28T02:00:00Z"));
        job2.setEndTime(Instant.parse("2026-10-28T03:00:00Z"));

        ServiceJob job3 = new ServiceJob();
        job3.setId(UUID.randomUUID());
        job3.setStatus(JobStatus.PENDING);

        a.setServiceJobs(List.of(job1, job2, job3));

        when(appointmentRepository.findWithJobsByIdAndCustomerId(id, CUSTOMER_ID))
                .thenReturn(Optional.of(a));
        when(dealershipValidation.getDealershipTimezone(DEALERSHIP_ID)).thenReturn("Asia/Ho_Chi_Minh");
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        CancelAppointmentResponse response = appointmentService.cancelAppointment(id, CUSTOMER_ID, CUSTOMER_PHONE);

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(job1.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job2.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job3.getStatus()).isEqualTo(JobStatus.CANCELLED);
        verify(jobTechnicianAssignmentRepository).deleteByJobIdIn(List.of(job1.getId(), job2.getId(), job3.getId()));
    }

    @Test
    void cancelAppointmentHandlesNullStartAndEndTimes() {
        UUID id = UUID.randomUUID();
        Appointment a = new Appointment();
        a.setId(id);
        a.setDealershipId(DEALERSHIP_ID);
        a.setCustomerId(CUSTOMER_ID);
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setStartTime(null);
        a.setEndTime(null);

        when(appointmentRepository.findWithJobsByIdAndCustomerId(id, CUSTOMER_ID))
                .thenReturn(Optional.of(a));
        when(dealershipValidation.getDealershipTimezone(DEALERSHIP_ID)).thenReturn("Asia/Ho_Chi_Minh");
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        CancelAppointmentResponse response = appointmentService.cancelAppointment(id, CUSTOMER_ID, CUSTOMER_PHONE);

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void appendJobNoteWhenCurrentNotesIsBlank() {
        UUID jobId = UUID.randomUUID();
        ServiceJob job = new ServiceJob();
        job.setId(jobId);
        job.setNotes("   ");

        when(serviceJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(serviceJobRepository.appendNoteAtomic(jobId, "new note")).thenReturn(1);

        AppointmentService.JobNoteResult result =
                appointmentService.appendJobNote(jobId, UUID.randomUUID(), "new note", true);

        assertThat(result.appended()).isEqualTo("new note");
        verify(serviceJobRepository).appendNoteAtomic(jobId, "new note");
    }

    @Test
    void appendJobNoteSucceedsForAssignedTechnician() {
        UUID jobId = UUID.randomUUID();
        String note = "Customer asked for tire check";

        JobTechnicianAssignment assignment = new JobTechnicianAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setJobId(jobId);
        assignment.setTechnicianId(TECHNICIAN_ID);
        assignment.setAssignedStartTime(Instant.parse("2026-10-28T01:00:00Z"));
        assignment.setAssignedEndTime(Instant.parse("2026-10-28T02:00:00Z"));

        ServiceJob job = new ServiceJob();
        job.setId(jobId);
        job.setNotes("existing note");

        when(jobTechnicianAssignmentRepository.findByJobId(jobId)).thenReturn(List.of(assignment));
        when(serviceJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(serviceJobRepository.appendNoteAtomic(jobId, note)).thenReturn(1);

        AppointmentService.JobNoteResult result = appointmentService.appendJobNote(jobId, TECHNICIAN_ID, note);

        assertThat(result.jobId()).isEqualTo(jobId);
        assertThat(result.appended()).isEqualTo(note);
        verify(serviceJobRepository).appendNoteAtomic(jobId, note);
    }

    @Test
    void readJobNotesSplitsStoredNotesIntoLines() {
        UUID jobId = UUID.randomUUID();
        ServiceJob job = new ServiceJob();
        job.setId(jobId);
        job.setNotes("first note\nsecond note");

        when(serviceJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        JobNoteResponse response = appointmentService.readJobNotes(jobId);

        assertThat(response.jobId()).isEqualTo(jobId);
        assertThat(response.notes()).containsExactly("first note", "second note");
    }

    @Test
    void readJobNotesReturnsEmptyListWhenNoNoteExists() {
        UUID jobId = UUID.randomUUID();
        ServiceJob job = new ServiceJob();
        job.setId(jobId);

        when(serviceJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThat(appointmentService.readJobNotes(jobId).notes()).isEmpty();
    }

    @Test
    void readJobNotesThrowsNotFoundWhenJobMissing() {
        UUID jobId = UUID.randomUUID();
        when(serviceJobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.readJobNotes(jobId))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void appendJobNoteRejectsUnassignedTechnician() {
        UUID jobId = UUID.randomUUID();

        JobTechnicianAssignment assignment = new JobTechnicianAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setJobId(jobId);
        assignment.setTechnicianId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        assignment.setAssignedStartTime(Instant.parse("2026-10-28T01:00:00Z"));
        assignment.setAssignedEndTime(Instant.parse("2026-10-28T02:00:00Z"));

        ServiceJob job = new ServiceJob();
        job.setId(jobId);

        when(jobTechnicianAssignmentRepository.findByJobId(jobId)).thenReturn(List.of(assignment));
        when(serviceJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> appointmentService.appendJobNote(jobId, TECHNICIAN_ID, "note"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @Test
    void appendJobNoteRejectsMissingJob() {
        UUID jobId = UUID.randomUUID();
        when(serviceJobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.appendJobNote(jobId, TECHNICIAN_ID, "note"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void appendJobNoteAllowsManagerRole() {
        UUID jobId = UUID.randomUUID();

        ServiceJob job = new ServiceJob();
        job.setId(jobId);

        when(serviceJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(serviceJobRepository.appendNoteAtomic(jobId, "manager note")).thenReturn(1);

        AppointmentService.JobNoteResult result =
                appointmentService.appendJobNote(jobId, UUID.randomUUID(), "manager note", true);

        assertThat(result.appended()).isEqualTo("manager note");
        verify(serviceJobRepository).appendNoteAtomic(jobId, "manager note");
    }

    @Test
    void appendJobNoteRejectsCustomerRole() {
        UUID jobId = UUID.randomUUID();
        when(serviceJobRepository.findById(jobId)).thenReturn(Optional.of(new ServiceJob()));

        assertThatThrownBy(() -> appointmentService.appendJobNote(jobId, UUID.randomUUID(), "x", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @Test
    void appendJobNoteRejectsTechnicianWithoutAssignment() {
        UUID jobId = UUID.randomUUID();
        when(jobTechnicianAssignmentRepository.findByJobId(jobId)).thenReturn(List.of());
        when(serviceJobRepository.findById(jobId)).thenReturn(Optional.of(new ServiceJob()));

        assertThatThrownBy(() -> appointmentService.appendJobNote(jobId, TECHNICIAN_ID, "note"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @Test
    void findStaffAppointmentReturnsCustomerJobNotesAndCommunicationLogs() {
        UUID appointmentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        StaffAppointmentQueryRepository.StaffAppointmentRow header = mockStaffHeader(appointmentId, customerId);
        StaffAppointmentQueryRepository.StaffJobRow job = mockStaffJob(jobId);
        StaffAppointmentQueryRepository.StaffCommunicationRow communication = mockStaffCommunication();

        when(staffAppointmentQueryRepository.findStaffAppointment(appointmentId))
                .thenReturn(Optional.of(header));
        when(staffAppointmentQueryRepository.findStaffJobs(appointmentId)).thenReturn(List.of(job));
        when(staffAppointmentQueryRepository.findStaffCommunications(appointmentId))
                .thenReturn(List.of(communication));

        StaffAppointmentResponse response = appointmentService.findStaffAppointment(appointmentId, DEALERSHIP_ID);

        assertThat(response.id()).isEqualTo(appointmentId);
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.staffView().customerName()).isEqualTo("Demo Customer");
        assertThat(response.staffView().customerPhone()).isEqualTo("+84900000004");
        assertThat(response.staffView().vehicleCategory()).isEqualTo("Honda Sedan");
        assertThat(response.staffView().jobs()).hasSize(1);
        assertThat(response.staffView().jobs().get(0).notes()).containsExactly("Tire wear found");
        assertThat(response.staffView().jobs().get(0).estimateCost()).isEqualByComparingTo("150.00");
        assertThat(response.staffView().jobs().get(0).finalCost()).isEqualByComparingTo("180.00");
        assertThat(response.staffView().communicationLogs()).hasSize(1);
        assertThat(response.staffView().communicationLogs().get(0).eventType()).isEqualTo("appointment.confirmed");
    }

    @Test
    void findStaffAppointmentReturnsNotFoundForForeignDealership() {
        UUID appointmentId = UUID.randomUUID();
        StaffAppointmentQueryRepository.StaffAppointmentRow header =
                org.mockito.Mockito.mock(StaffAppointmentQueryRepository.StaffAppointmentRow.class);
        when(header.getDealershipId()).thenReturn(DEALERSHIP_ID);

        when(staffAppointmentQueryRepository.findStaffAppointment(appointmentId))
                .thenReturn(Optional.of(header));

        assertThatThrownBy(() -> appointmentService.findStaffAppointment(
                        appointmentId, UUID.fromString("00000000-0000-0000-0000-000000000009")))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void findStaffAppointmentReturnsNotFoundWhenMissing() {
        UUID appointmentId = UUID.randomUUID();
        when(staffAppointmentQueryRepository.findStaffAppointment(appointmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.findStaffAppointment(appointmentId, DEALERSHIP_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    private static StaffAppointmentQueryRepository.StaffAppointmentRow mockStaffHeader(
            UUID appointmentId, UUID customerId) {
        StaffAppointmentQueryRepository.StaffAppointmentRow row =
                org.mockito.Mockito.mock(StaffAppointmentQueryRepository.StaffAppointmentRow.class);
        when(row.getId()).thenReturn(appointmentId);
        when(row.getDealershipId()).thenReturn(DEALERSHIP_ID);
        when(row.getCustomerId()).thenReturn(customerId);
        when(row.getVehicleVin()).thenReturn(VIN);
        when(row.getStatus()).thenReturn("CONFIRMED");
        when(row.getStartTime()).thenReturn(Instant.parse("2026-10-28T01:00:00Z"));
        when(row.getEndTime()).thenReturn(Instant.parse("2026-10-28T02:00:00Z"));
        when(row.getEstimateCost()).thenReturn(new BigDecimal("150.00"));
        when(row.getTotalCost()).thenReturn(null);
        when(row.getCreatedAt()).thenReturn(Instant.parse("2026-10-27T10:00:00Z"));
        when(row.getCustomerPhone()).thenReturn("+84900000004");
        when(row.getCustomerName()).thenReturn("Demo Customer");
        when(row.getVehicleCategory()).thenReturn("Honda Sedan");
        return row;
    }

    private static StaffAppointmentQueryRepository.StaffJobRow mockStaffJob(UUID jobId) {
        StaffAppointmentQueryRepository.StaffJobRow row =
                org.mockito.Mockito.mock(StaffAppointmentQueryRepository.StaffJobRow.class);
        when(row.getJobId()).thenReturn(jobId);
        when(row.getServiceName()).thenReturn("STANDARD_SERVICE");
        when(row.getBayName()).thenReturn("Bay 2");
        when(row.getStatus()).thenReturn("DONE");
        when(row.getStartTime()).thenReturn(Instant.parse("2026-10-28T01:00:00Z"));
        when(row.getEndTime()).thenReturn(Instant.parse("2026-10-28T02:00:00Z"));
        when(row.getNotes()).thenReturn("Tire wear found");
        when(row.getEstimateCost()).thenReturn(new BigDecimal("150.00"));
        when(row.getFinalCost()).thenReturn(new BigDecimal("180.00"));
        return row;
    }

    private static StaffAppointmentQueryRepository.StaffCommunicationRow mockStaffCommunication() {
        StaffAppointmentQueryRepository.StaffCommunicationRow row =
                org.mockito.Mockito.mock(StaffAppointmentQueryRepository.StaffCommunicationRow.class);
        when(row.getEventType()).thenReturn("appointment.confirmed");
        when(row.getChannel()).thenReturn("SSE");
        when(row.getDeliveryStatus()).thenReturn("DELIVERED");
        when(row.getDispatchedAt()).thenReturn(Instant.parse("2026-10-27T10:05:00Z"));
        return row;
    }
}
