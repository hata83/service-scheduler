package dev.hata.scheduler.common.idempotency;

/**
 * Stores the response of a write so a retry with the same key, caller scope,
 * and request payload returns the first response (ADR-008 / design D8).
 * Never caches or replays {@code Set-Cookie} bearer headers.
 */
public interface IdempotencyStore {

    /**
     * Builds the namespaced record key.
     *
     * @param endpoint the HTTP method and path
     * @param scope the caller identity and SHA-256 request payload hash
     * @param headerValue the client-supplied header value
     */
    String slot(String endpoint, String scope, String headerValue);

    /**
     * Atomically claims an in-flight lock for the given slot with a unique owner token.
     */
    default boolean tryClaim(String slot, String ownerToken) {
        return true;
    }

    /**
     * Atomically releases the in-flight lock only if owned by {@code ownerToken}.
     */
    default void releaseClaim(String slot, String ownerToken) {}

    /**
     * Returns the cached response for a slot, or null when the slot is free.
     */
    CachedResponse load(String slot);

    /**
     * Stores the response snapshot bounded by the session token TTL.
     */
    void store(String slot, CachedResponse response);

    /**
     * The response snapshot a replay returns (status, content type, and body).
     */
    record CachedResponse(int status, String body, String contentType) {
        public CachedResponse(int status, String body) {
            this(status, body, "application/json;charset=UTF-8");
        }
    }
}
