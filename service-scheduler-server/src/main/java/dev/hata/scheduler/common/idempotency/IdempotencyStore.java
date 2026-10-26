package dev.hata.scheduler.common.idempotency;

/**
 * Stores the response of a write so a retry with the same key returns the
 * first response (ADR-008 / design D8).
 */
public interface IdempotencyStore {

    /**
     * Builds the namespaced record key.
     *
     * @param endpoint the operation name, for example {@code register}
     * @param phone the E.164 phone number of the caller
     * @param headerValue the client-supplied header value
     */
    String slot(String endpoint, String phone, String headerValue);

    /**
     * Returns the cached response for a slot, or null when the slot is free.
     */
    CachedResponse load(String slot);

    /**
     * Stores the response snapshot for 24 hours.
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
