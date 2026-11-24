package dev.omercanbasboga.monitoringhub.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Read-through cache on Redis.
 *
 * It fails open: if Redis is down or switched off, reads go to the database and
 * writes are dropped. The cache makes the dashboard fast; it must never be the
 * reason a request fails.
 *
 * Values are stored as plain JSON and read back with an explicit
 * {@link TypeReference}. No Jackson type ids in the payload, so moving a DTO to
 * another package doesn't poison what's already cached.
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    /** Log the first Redis error and then every Nth one, not all of them. */
    private static final long ERROR_LOG_EVERY = 100;

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final MeterRegistry meters;
    private final boolean enabled;
    private final AtomicLong errors = new AtomicLong();

    public CacheService(ObjectProvider<StringRedisTemplate> redis, ObjectMapper json, MeterRegistry meters,
                        @Value("${cache.enabled:false}") boolean enabled) {
        this.redis = redis.getIfAvailable();
        this.json = json;
        this.meters = meters;
        this.enabled = enabled && this.redis != null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public <T> T getOrLoad(String key, TypeReference<T> type, Duration ttl, Supplier<T> loader) {
        T cached = get(key, type);
        if (cached != null) {
            count("hit");
            return cached;
        }
        count(enabled ? "miss" : "off");
        T loaded = loader.get();
        put(key, loaded, ttl);
        return loaded;
    }

    public <T> T get(String key, TypeReference<T> type) {
        if (!enabled) return null;
        try {
            String raw = redis.opsForValue().get(key);
            return raw == null ? null : json.readValue(raw, type);
        } catch (Exception e) {
            onError("read", key, e);
            return null;
        }
    }

    public void put(String key, Object value, Duration ttl) {
        if (!enabled || value == null) return;
        try {
            redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
        } catch (Exception e) {
            onError("write", key, e);
        }
    }

    public void evict(String key) {
        if (!enabled) return;
        try {
            redis.delete(key);
        } catch (Exception e) {
            onError("delete", key, e);
        }
    }

    /** Counted separately so we can see how often the 24 h slice saves a database trip. */
    public void recordSliceHit() {
        count("slice");
    }

    private void onError(String op, String key, Exception e) {
        count("error");
        long n = errors.incrementAndGet();
        if (n == 1 || n % ERROR_LOG_EVERY == 0) {
            log.warn("Redis {} failed for {} ({} errors so far), falling back to the database: {}",
                    op, key, n, e.getMessage());
        }
    }

    private void count(String result) {
        meters.counter("monitoring_hub_cache_requests_total", "result", result).increment();
    }
}
