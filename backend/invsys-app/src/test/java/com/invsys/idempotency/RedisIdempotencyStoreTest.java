package com.invsys.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisIdempotencyStoreTest {

    @Test
    void localFallbackCachesAndReplays() {
        @SuppressWarnings("unchecked")
        ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        RedisIdempotencyStore store = new RedisIdempotencyStore(provider);
        UUID tenant = UUID.randomUUID();
        String key = "scan-" + UUID.randomUUID();
        byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);

        assertThat(store.get(tenant, key)).isEmpty();
        store.put(tenant, key, new RedisIdempotencyStore.CachedResponse(200, "application/json", body));

        Optional<RedisIdempotencyStore.CachedResponse> hit = store.get(tenant, key);
        assertThat(hit).isPresent();
        assertThat(hit.get().status()).isEqualTo(200);
        assertThat(new String(hit.get().body(), StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
    }

    @Test
    void encodeDecodeRoundTrip() {
        byte[] body = "{\"action\":\"PO_RECEIPT\"}".getBytes(StandardCharsets.UTF_8);
        RedisIdempotencyStore.CachedResponse original =
                new RedisIdempotencyStore.CachedResponse(200, "application/json", body);
        RedisIdempotencyStore.CachedResponse decoded =
                RedisIdempotencyStore.decode(RedisIdempotencyStore.encode(original));
        assertThat(decoded.status()).isEqualTo(200);
        assertThat(decoded.contentType()).isEqualTo("application/json");
        assertThat(decoded.body()).isEqualTo(body);
    }
}
