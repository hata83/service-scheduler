package dev.hata.scheduler.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiProblem> handleOther(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return build(status, status.getReasonPhrase(), ex.getMessage(), request);
        }
        // A server failure can carry a database or a network message. The
        // response stays generic and the cause goes to the log.
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
