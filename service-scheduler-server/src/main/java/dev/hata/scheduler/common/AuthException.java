package dev.hata.scheduler.common;

import org.springframework.http.HttpStatus;

/**
 * Carries an HTTP status and an RFC 7807 detail message. The global handler
 * converts it into an {@code application/problem+json} body (ADR-006).
 */
public class AuthException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String title;

    public AuthException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public AuthException(HttpStatus status, String title, String detail, Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.title = title;
    }

    public HttpStatus httpStatus() {
        return status;
    }

    public String problemTitle() {
        return title;
    }
}
