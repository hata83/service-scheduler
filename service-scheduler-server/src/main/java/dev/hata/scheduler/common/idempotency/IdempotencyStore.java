package dev.hata.scheduler.common.idempotency;

/**
 * Stores the response of a write so a retry with the same key and request
 * payload returns the first response (ADR-008 / design D8).
 */
public interface IdempotencyStore {

    /**
     * Builds the namespaced record key.
     *
     * @param endpoint the HTTP method and path
     * @param scope the request payload hash or caller scope
     * @param headerValue the client-supplied header value
     */
    String slot(String endpoint, String scope, String headerValue);

    /**
     * Atomically claims an in-flight lock for the given slot so concurrent
     * identical requests cannot race.
     */
    default boolean tryClaim(String slot) {
        return true;
    }

    /**
     * Releases the in-flight lock for the given slot.
     */
    default void releaseClaim(String slot) {}

    /**
     * Returns the cached response for a slot, or null when the slot is free.
     */
    CachedResponse load(String slot);

    /**
     * Stores the response snapshot bounded by the session token TTL.
     */
    void store(String slot, CachedResponse response);

    /**
     * The response snapshot a replay returns.
     */
    record CachedResponse(int status, String body, String setCookie) {

        public boolean hasSetCookie() {
            return setCookie != null && !setCookie.isBlank();
        }
    }
}
