package dev.omercanbasboga.monitoringhub.cache;

import dev.omercanbasboga.monitoringhub.model.Station;
import dev.omercanbasboga.monitoringhub.repository.StationRepository;
import dev.omercanbasboga.monitoringhub.service.ReadingLoader;
import dev.omercanbasboga.monitoringhub.service.StationDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Rewrites the hot keys before they expire, so a dashboard left open never
 * waits on the database.
 *
 * Only stations someone has read in the last {@code cache.warmup.keep-for}
 * are warmed. Warming every active station would turn the cache into a steady
 * background load on the database it is meant to protect.
 *
 * It writes instead of evicting. Evict-then-load leaves a window where a burst
 * of requests all miss together and hit the database at once.
 */
@Component
public class CacheWarmup {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmup.class);

    private final CacheService cache;
    private final CacheTtl ttl;
    private final StationRepository stations;
    private final StationDataService data;
    private final ReadingLoader loader;
    private final Duration keepFor;

    public CacheWarmup(CacheService cache, CacheTtl ttl, StationRepository stations, StationDataService data,
                       ReadingLoader loader, @Value("${cache.warmup.keep-for:PT10M}") Duration keepFor) {
        this.cache = cache;
        this.ttl = ttl;
        this.stations = stations;
        this.data = data;
        this.loader = loader;
        this.keepFor = keepFor;
    }

    @Scheduled(fixedDelayString = "${cache.warmup.delay:PT45S}", initialDelayString = "${cache.warmup.initial-delay:PT10S}")
    public void warm() {
        if (!cache.isEnabled()) return;
        long started = System.currentTimeMillis();
        Instant end = Instant.now();
        Instant since = end.minus(keepFor);
        Set<UUID> hot = data.stationsReadSince(since);

        for (Station s : stations.findAllById(hot)) {
            try {
                long threshold = data.gapThresholdSeconds(s);
                cache.put(CacheKeys.last24h(s.getId(), threshold),
                        loader.readings(s, end.minusSeconds(24 * 3600), end, threshold), ttl.last24h());
            } catch (Exception e) {
                log.warn("Warm-up skipped station {}: {}", s.getExternalId(), e.getMessage());
            }
        }
        if (data.unifiedReadSince(since)) {
            cache.put(CacheKeys.unifiedStations(), loader.unifiedStations(), ttl.stations());
        }
        log.debug("Warmed {} stations in {} ms", hot.size(), System.currentTimeMillis() - started);
    }
}
