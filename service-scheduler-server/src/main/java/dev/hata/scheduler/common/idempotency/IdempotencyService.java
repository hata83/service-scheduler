package dev.hata.scheduler.common.idempotency;

import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Service for checking, loading, and persisting idempotent request/response records.
 */
@Service
public class IdempotencyService {

    private final IdempotencyStore idempotencyStore;

    public IdempotencyService(IdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
    }

    public String slot(String endpoint, String qualifier, String idempotencyKey) {
        return idempotencyStore.slot(endpoint, qualifier, idempotencyKey);
    }

    public Optional<IdempotencyStore.CachedResponse> load(String slot) {
        return Optional.ofNullable(idempotencyStore.load(slot));
    }

    public void store(String slot, IdempotencyStore.CachedResponse response) {
        idempotencyStore.store(slot, response);
    }
}
