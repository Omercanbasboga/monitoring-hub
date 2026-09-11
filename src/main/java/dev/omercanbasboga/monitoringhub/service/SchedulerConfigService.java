package dev.omercanbasboga.monitoringhub.service;

import dev.omercanbasboga.monitoringhub.model.SchedulerConfig;
import dev.omercanbasboga.monitoringhub.model.SourceType;
import dev.omercanbasboga.monitoringhub.repository.SchedulerConfigRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps each source's polling interval (ms) in the DB and caches it in memory.
 * {@code DynamicIntervalTrigger} re-reads the cache on every firing, so a
 * change made through {@link dev.omercanbasboga.monitoringhub.controller.SchedulerConfigController}
 * takes effect on the next tick without a restart.
 */
@Service
public class SchedulerConfigService {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerConfigService.class);

    public static final long MIN_INTERVAL_MS = 5_000L;
    public static final long MAX_INTERVAL_MS = 600_000L;
    private static final long DEFAULT_INTERVAL_MS = 30_000L;

    private final SchedulerConfigRepository repository;
    private final Map<String, AtomicLong> cache = new ConcurrentHashMap<>();

    public SchedulerConfigService(SchedulerConfigRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        for (SourceType source : SourceType.values()) {
            String key = source.name();
            SchedulerConfig config = repository.findById(key).orElse(null);
            if (config == null) {
                config = new SchedulerConfig();
                config.setSourceName(key);
                config.setIntervalMs(DEFAULT_INTERVAL_MS);
                config.setUpdatedAt(LocalDateTime.now());
                repository.save(config);
                logger.info("Default polling interval stored for {}: {}ms", key, DEFAULT_INTERVAL_MS);
            }
            cache.put(key, new AtomicLong(config.getIntervalMs()));
        }
    }

    public boolean isKnownSource(String source) {
        return cache.containsKey(source);
    }

    public long getIntervalMs(String source) {
        AtomicLong value = cache.get(source);
        if (value == null) throw new IllegalArgumentException("Unknown source: " + source);
        return value.get();
    }

    public Map<String, Long> getAllIntervals() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (SourceType source : SourceType.values()) result.put(source.name(), getIntervalMs(source.name()));
        return result;
    }

    public long updateInterval(String source, long newIntervalMs) {
        if (!isKnownSource(source)) throw new IllegalArgumentException("Unknown source: " + source);
        if (newIntervalMs < MIN_INTERVAL_MS || newIntervalMs > MAX_INTERVAL_MS) {
            throw new IllegalArgumentException("Interval must be between " + MIN_INTERVAL_MS + "ms and " + MAX_INTERVAL_MS + "ms");
        }

        SchedulerConfig config = repository.findById(source).orElseGet(() -> {
            SchedulerConfig c = new SchedulerConfig();
            c.setSourceName(source);
            return c;
        });
        config.setIntervalMs(newIntervalMs);
        config.setUpdatedAt(LocalDateTime.now());
        repository.save(config);

        cache.get(source).set(newIntervalMs);
        logger.info("Polling interval updated for {}: {}ms", source, newIntervalMs);
        return newIntervalMs;
    }
}
