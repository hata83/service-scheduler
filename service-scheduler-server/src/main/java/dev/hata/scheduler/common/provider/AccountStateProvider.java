package dev.hata.scheduler.common.provider;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for server-side revalidation of caller account state (active status,
 * role, and dealership scope) on every request without coupling to the auth entity model.
 */
public interface AccountStateProvider {

    /**
     * Retrieves the current server-side state for the given user.
     *
     * @param userId the user UUID
     * @return an {@link AccountState} if the user exists, or empty if not found
     */
    Optional<AccountState> getAccountState(UUID userId);

    /**
     * Snapshot of the user's active status, authorization role, and scoped dealership.
     */
    record AccountState(boolean active, String role, UUID dealershipId) {}
}
