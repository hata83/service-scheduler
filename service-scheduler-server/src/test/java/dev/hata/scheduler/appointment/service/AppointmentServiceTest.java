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
import dev.hata.scheduler.appointment.dto.ServiceJobItemRequest;
import dev.hata.scheduler.appointment.model.Appointment;
import dev.hata.scheduler.appointment.model.AppointmentStatus;
import dev.hata.scheduler.appointment.model.JobStatus;
import dev.hata.scheduler.appointment.model.ServiceJob;
import dev.hata.scheduler.appointment.repository.AppointmentRepository;
import dev.hata.scheduler.appointment.repository.JobTechnicianAssignmentRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
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

        verify(slotCacheEviction).evictSlots(any(), any());
        verify(eventPublisher).publishEvent(any(NotificationDispatchEvent.class));
    }

    @Test
    void bookSetsRequestedStatusWhenAutoConfirmFalse() {
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
    void bookReplayingKeyFromDifferentCustomerThrowsConflict() {
        UUID key = UUID.randomUUID();
        UUID otherCustomer = UUID.randomUUID();
        Appointment existing = new Appointment();
        existing.setId(UUID.randomUUID());
        existing.setCustomerId(otherCustomer);
        existing.setDealershipId(DEALERSHIP_ID);
        existing.setVehicleCategoryId(CATEGORY_ID);
        existing.setVehicleVin(VIN);
        existing.setStartTime(Instant.now());
        existing.setEndTime(Instant.now().plus(Duration.ofMinutes(30)));
        existing.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findWithJobsByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                CATEGORY_ID,
                VIN,
                List.of(new ServiceJobItemRequest(
                        SERVICE_TYPE_1, BAY_1, Instant.now(), Instant.now().plus(Duration.ofMinutes(30)))));

        assertThatThrownBy(() -> appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, key))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Idempotency key already used by another customer");
    }

    @Test
    void bookRejectsOverlappingServiceJobsWithinAppointment() {
        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(categoryValidation.existsCategoryInDealership(DEALERSHIP_ID, CATEGORY_ID))
                .thenReturn(true);

        Instant start = Instant.parse("2026-10-28T01:00:00Z");
        Instant overlap = start.plus(Duration.ofMinutes(15));
        Instant end1 = start.plus(Duration.ofMinutes(30));
        Instant end2 = overlap.plus(Duration.ofMinutes(30));

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                CATEGORY_ID,
                VIN,
                List.of(
                        new ServiceJobItemRequest(SERVICE_TYPE_1, BAY_1, start, end1),
                        new ServiceJobItemRequest(SERVICE_TYPE_2, BAY_2, overlap, end2)));

        assertThatThrownBy(() -> appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
                .hasMessageContaining("Service job time slots for vehicle cannot overlap");
    }

    @Test
    void bookRejectsServiceJobWhenDurationExceedsBaseDuration() {
        when(dealershipValidation.existsActiveDealership(DEALERSHIP_ID)).thenReturn(true);
        when(categoryValidation.existsCategoryInDealership(DEALERSHIP_ID, CATEGORY_ID))
                .thenReturn(true);
        when(dealershipValidation.existsActiveBay(DEALERSHIP_ID, BAY_1)).thenReturn(true);

        when(serviceTypeMetadataProvider.findServiceTypeMetadata(DEALERSHIP_ID, CATEGORY_ID, SERVICE_TYPE_1))
                .thenReturn(Optional.of(new ServiceTypeMetadata(SERVICE_TYPE_1, 30, new BigDecimal("60.00"))));

        Instant start = Instant.parse("2026-10-28T01:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(45)); // 45 min > 30 min base!

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID, CATEGORY_ID, VIN, List.of(new ServiceJobItemRequest(SERVICE_TYPE_1, BAY_1, start, end)));

        assertThatThrownBy(() -> appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
                .hasMessageContaining("exceeds base duration");
    }

    @Test
    void findCustomerAppointmentsReturnsOrderedList() {
        Appointment a = new Appointment();
        a.setId(UUID.randomUUID());
        a.setDealershipId(DEALERSHIP_ID);
        a.setCustomerId(CUSTOMER_ID);
        a.setVehicleCategoryId(CATEGORY_ID);
        a.setVehicleVin(VIN);
        a.setStartTime(Instant.now());
        a.setEndTime(Instant.now().plus(Duration.ofHours(1)));
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setEstimateCost(new BigDecimal("100.00"));

        when(appointmentRepository.findByCustomerIdOrderByStartTimeDesc(CUSTOMER_ID))
                .thenReturn(List.of(a));

        List<AppointmentResponse> list = appointmentService.findCustomerAppointments(CUSTOMER_ID);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).id()).isEqualTo(a.getId());
    }

    @Test
    void findAppointmentByIdReturnsFullDetails() {
        UUID id = UUID.randomUUID();
        Appointment a = new Appointment();
        a.setId(id);
        a.setDealershipId(DEALERSHIP_ID);
        a.setCustomerId(CUSTOMER_ID);
        a.setVehicleCategoryId(CATEGORY_ID);
        a.setVehicleVin(VIN);
        a.setStartTime(Instant.now());
        a.setEndTime(Instant.now().plus(Duration.ofHours(1)));
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setEstimateCost(new BigDecimal("100.00"));

        when(appointmentRepository.findWithJobsByIdAndCustomerId(id, CUSTOMER_ID))
                .thenReturn(Optional.of(a));

        AppointmentResponse resp = appointmentService.findAppointmentById(id, CUSTOMER_ID);
        assertThat(resp.id()).isEqualTo(id);
    }

    @Test
    void findAppointmentByIdThrows404WhenOwnedByOtherCustomer() {
        UUID id = UUID.randomUUID();
        when(appointmentRepository.findWithJobsByIdAndCustomerId(id, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.findAppointmentById(id, CUSTOMER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelAppointmentSucceedsForConfirmedStatus() {
        UUID id = UUID.randomUUID();
        Appointment a = new Appointment();
        a.setId(id);
        a.setDealershipId(DEALERSHIP_ID);
        a.setCustomerId(CUSTOMER_ID);
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setStartTime(Instant.parse("2026-10-28T01:00:00Z"));
        a.setEndTime(Instant.parse("2026-10-28T02:00:00Z"));

        ServiceJob job = new ServiceJob();
        job.setStatus(JobStatus.PENDING);
        a.addServiceJob(job);

        when(appointmentRepository.findWithJobsByIdAndCustomerId(id, CUSTOMER_ID))
                .thenReturn(Optional.of(a));
        when(dealershipValidation.getDealershipTimezone(DEALERSHIP_ID)).thenReturn("Asia/Ho_Chi_Minh");
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        CancelAppointmentResponse response = appointmentService.cancelAppointment(id, CUSTOMER_ID, CUSTOMER_PHONE);

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.getUpdatedBy()).isEqualTo(CUSTOMER_ID);
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.getUpdatedBy()).isEqualTo(CUSTOMER_ID);
        verify(jobTechnicianAssignmentRepository).deleteByJobIdIn(any());
        verify(slotCacheEviction).evictSlots(any(), any());
        verify(eventPublisher).publishEvent(any(NotificationDispatchEvent.class));
    }

    @Test
    void cancelAppointmentThrows422WhenCheckedInOrLater() {
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
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY)
                .hasMessageContaining("Cancellation permitted prior to check-in only");
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
}
