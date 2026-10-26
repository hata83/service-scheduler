package dev.hata.scheduler.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyStore store;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(store);
    }

    @Test
    void delegatesSlotConstruction() {
        when(store.slot("endpoint", "phone", "key")).thenReturn("slot-123");

        assertThat(service.slot("endpoint", "phone", "key")).isEqualTo("slot-123");
    }

    @Test
    void delegatesLoad() {
        IdempotencyStore.CachedResponse cached = new IdempotencyStore.CachedResponse(200, "body", "cookie");
        when(store.load("slot-123")).thenReturn(cached);

        Optional<IdempotencyStore.CachedResponse> loaded = service.load("slot-123");
        assertThat(loaded).contains(cached);
    }

    @Test
    void delegatesStore() {
        IdempotencyStore.CachedResponse cached = new IdempotencyStore.CachedResponse(200, "body", "cookie");
        service.store("slot-123", cached);

        verify(store).store("slot-123", cached);
    }
}
