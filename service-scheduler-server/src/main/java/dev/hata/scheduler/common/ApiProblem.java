package dev.hata.scheduler.common;

/**
 * RFC 7807 problem details for every error response (ADR-006).
 * The body carries type, title, status, detail, instance and timestamp.
 */
public record ApiProblem(String type, String title, int status, String detail, String instance, String timestamp) {

    public static ApiProblem of(int status, String title, String detail, String instance) {
        return new ApiProblem(
                "/errors/" + status,
                title,
                status,
                detail,
                instance,
                java.time.Instant.now().toString());
    }
}
