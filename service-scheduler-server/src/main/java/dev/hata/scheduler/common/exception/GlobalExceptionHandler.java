package dev.hata.scheduler.common.exception;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Converts every failure into an RFC 7807 problem+json body (ADR-006).
 * A Spring exception that already carries a status keeps that status, so a
 * missing resource answers 404 and not 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiProblem> handleAuth(AuthException ex, HttpServletRequest request) {
        return build(ex.httpStatus(), ex.problemTitle(), ex.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiProblem> handleInvalid(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid request body", ex.getMessage(), request);
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiProblem> handleTypeMismatch(TypeMismatchException ex, HttpServletRequest request) {
        String paramName;
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            paramName = mismatch.getName();
        } else if (ex.getPropertyName() != null) {
            paramName = ex.getPropertyName();
        } else {
            paramName = "unknown";
        }
        String detail = "Invalid request parameter '" + paramName + "': "
                + (ex.getValue() != null ? ex.getValue().toString() : "invalid value");
        return build(HttpStatus.BAD_REQUEST, "Invalid request parameter", detail, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiProblem> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        String msg = ex.getMessage();
        Throwable cause = ex.getRootCause();
        String rootMsg = cause != null && cause.getMessage() != null ? cause.getMessage() : "";
        String combined = (msg + " " + rootMsg).toLowerCase(Locale.ROOT);

        if (combined.contains("no_vehicle_overlap")) {
            return build(
                    HttpStatus.CONFLICT,
                    "Vehicle schedule conflict",
                    "A booking already exists for this vehicle during the requested time window.",
                    request);
        }
        if (combined.contains("no_bay_overlap")) {
            return build(
                    HttpStatus.CONFLICT,
                    "Workshop bay conflict",
                    "The selected workshop bay is already reserved during the requested time window.",
                    request);
        }
        if (combined.contains("no_technician_overlap")) {
            return build(
                    HttpStatus.CONFLICT,
                    "Technician schedule conflict",
                    "The assigned technician is already booked during the requested time window.",
                    request);
        }
        if (combined.contains("users_phone_key")) {
            return build(
                    HttpStatus.CONFLICT,
                    "Phone already registered",
                    "This phone already belongs to an account. Use the login flow instead.",
                    request);
        }
        log.error("Data integrity failure on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(
                HttpStatus.CONFLICT,
                "Data integrity conflict",
                "The request violates a database integrity constraint.",
                request);
    }

    @ExceptionHandler({
        org.springframework.orm.ObjectOptimisticLockingFailureException.class,
        jakarta.persistence.OptimisticLockException.class
    })
    public ResponseEntity<ApiProblem> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        log.warn(
                "Optimistic lock conflict on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(
                HttpStatus.CONFLICT,
                "Concurrent modification conflict",
                "The requested resource was modified concurrently. Please reload and try again.",
                request);
    }

    /**
     * Maps lock deadlocks and lock-wait failures to a conflict. Two bookings
     * that race for the same bay and technician interleave their exclusion
     * constraint checks; PostgreSQL aborts one transaction. The customer sees
     * a retryable conflict instead of a 500 (SYSTEM_DESIGN 5.4).
     */
    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ApiProblem> handleConcurrentLock(CannotAcquireLockException ex, HttpServletRequest request) {
        log.warn(
                "Lock acquisition failure on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(
                HttpStatus.CONFLICT,
                "Concurrent request conflict",
                "The request raced another request for the same resource. Please retry.",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiProblem> handleOther(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return build(status, status.getReasonPhrase(), ex.getMessage(), request);
        }
        // A server failure can carry a database or a network message. The
        // response stays generic and the cause goes to the log.
        if (ex.getCause() != null
                && ex.getCause().getMessage() != null
                && ex.getCause().getMessage().contains("users_phone_key")) {
            return build(
                    HttpStatus.CONFLICT,
                    "Phone already registered",
                    "This phone already belongs to an account. Use the login flow instead.",
                    request);
        }
        log.error("Unhandled failure on {} {}", request.getMethod(), request.getRequestURI(), ex);
        String detail = "The request failed. Give the request id to the support team.";
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", detail, request);
    }

    private ResponseEntity<ApiProblem> build(
            HttpStatus status, String title, String detail, HttpServletRequest request) {
        ApiProblem problem =
                ApiProblem.of(status.value(), title, detail == null ? title : detail, request.getRequestURI());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
