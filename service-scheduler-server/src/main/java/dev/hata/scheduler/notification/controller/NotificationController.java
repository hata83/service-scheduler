package dev.hata.scheduler.notification.controller;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.exception.ApiProblem;
import dev.hata.scheduler.common.filter.AuthFilter;
import dev.hata.scheduler.common.security.AuthPrincipal;
import dev.hata.scheduler.common.security.StaffScope;
import dev.hata.scheduler.notification.dto.CommunicationLogResponse;
import dev.hata.scheduler.notification.service.NotificationService;
import dev.hata.scheduler.notification.service.SseEmitterRegistry;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Controller exposing real-time SSE notification stream and communication history.
 */
@Tag(name = "Notifications", description = "Real-time SSE notification stream and audit log endpoints")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);
    private static final String ROLE_CUSTOMER = "CUSTOMER";

    private final NotificationService notificationService;
    private final SseEmitterRegistry sseEmitterRegistry;

    public NotificationController(NotificationService notificationService, SseEmitterRegistry sseEmitterRegistry) {
        this.notificationService = notificationService;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    /**
     * Connects an authenticated client to the real-time Server-Sent Events notification stream.
     */
    @Operation(
            summary = "Open real-time SSE notification stream",
            description =
                    "Opens a persistent text/event-stream connection via JWT cookie. Reconnect evicts prior stream.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "SSE connection established successfully",
                content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class)))
    })
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestAttribute(name = AuthFilter.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal,
            @RequestParam(name = "closeAfterInitial", required = false, defaultValue = "false")
                    boolean closeAfterInitial,
            HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (closeAfterInitial) {
            SseEmitter standalone = new SseEmitter(0L);
            try {
                standalone.send(SseEmitter.event().comment("connected"));
            } catch (Exception e) {
                log.debug("Failed sending initial SSE comment for phone: {}", maskPhone(principal.phone()), e);
            }
            try {
                standalone.complete();
            } catch (Exception ignored) {
            }
            return standalone;
        }

        if (StaffScope.isStaffRole(principal.role())) {
            return sseEmitterRegistry.registerStaff(principal.phone(), principal.dealershipId());
        }
        return sseEmitterRegistry.register(principal.phone());
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() <= 3) {
            return "***";
        }
        return "***" + phone.substring(phone.length() - 3);
    }

    /**
     * Returns notification audit history for the authenticated caller.
     */
    @Operation(
            summary = "Get notification history",
            description = "Retrieves communication audit records dispatched to the authenticated user")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Communication logs retrieved successfully",
                content =
                        @Content(
                                mediaType = "application/json",
                                array =
                                        @ArraySchema(
                                                schema = @Schema(implementation = CommunicationLogResponse.class)))),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class)))
    })
    @GetMapping(
            value = "/my",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
    public ResponseEntity<List<CommunicationLogResponse>> myNotifications(
            @RequestAttribute(name = AuthFilter.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (ROLE_CUSTOMER.equals(principal.role())) {
            return ResponseEntity.ok(notificationService.findCustomerLogs(principal.userId()));
        }
        if (StaffScope.ROLE_MANAGER.equals(principal.role())) {
            UUID scope = StaffScope.dealershipScope(principal, null);
            return ResponseEntity.ok(notificationService.findStaffLogs(scope, principal.phone()));
        }
        if (StaffScope.ROLE_ADMIN.equals(principal.role())) {
            return ResponseEntity.ok(notificationService.findStaffLogs(null, principal.phone()));
        }
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Only customer, manager, and admin accounts can read notifications");
    }
}
