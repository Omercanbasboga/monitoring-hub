package dev.omercanbasboga.monitoringhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.omercanbasboga.monitoringhub.cache.CacheKeys;
import dev.omercanbasboga.monitoringhub.cache.CacheService;
import dev.omercanbasboga.monitoringhub.cache.CacheSlice;
import dev.omercanbasboga.monitoringhub.cache.CacheTtl;
import dev.omercanbasboga.monitoringhub.dto.ReadingDto;
import dev.omercanbasboga.monitoringhub.dto.UnifiedStationDto;
import dev.omercanbasboga.monitoringhub.model.Reading;
import dev.omercanbasboga.monitoringhub.model.SourceType;
import dev.omercanbasboga.monitoringhub.model.Station;
import dev.omercanbasboga.monitoringhub.repository.ReadingRepository;
import dev.omercanbasboga.monitoringhub.repository.StationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Shared read/write layer used by every ingestion connector and by the API
 * controllers. Centralizes station upsert, dedup-on-write, chart-oriented
 * reads (gap-fill then downsample) and the cross-source "unified stations"
 * view.
 */
@Service
public class StationDataService {

    private static final int CHART_MAX_BUCKETS = 300;
    private static final TypeReference<List<ReadingDto>> READINGS = new TypeReference<>() {};
    private static final TypeReference<List<UnifiedStationDto>> STATIONS = new TypeReference<>() {};

    private final StationRepository stationRepository;
    private final ReadingRepository readingRepository;
    private final ReadingLoader loader;
    private final ChartDownsampler downsampler;
    private final SchedulerConfigService scheduler;
    private final CacheService cache;
    private final CacheTtl ttl;
    private final Map<UUID, Instant> recentReads = new ConcurrentHashMap<>();
    private volatile Instant unifiedReadAt;

    public StationDataService(StationRepository stationRepository, ReadingRepository readingRepository,
                               ReadingLoader loader, ChartDownsampler downsampler,
                               SchedulerConfigService scheduler, CacheService cache, CacheTtl ttl) {
        this.stationRepository = stationRepository;
        this.readingRepository = readingRepository;
        this.loader = loader;
        this.downsampler = downsampler;
        this.scheduler = scheduler;
        this.cache = cache;
        this.ttl = ttl;
    }

    @Transactional
    public Station findOrCreateStation(SourceType sourceType, String externalId, String name) {
        return stationRepository.findBySourceTypeAndExternalId(sourceType, externalId)
                .orElseGet(() -> stationRepository.save(new Station(sourceType, externalId, name)));
    }

    /**
     * Saves readings that aren't already present for this station+timestamp.
     * Every connector calls this after parsing a batch, so dedup-on-write
     * lives in exactly one place instead of one copy per source.
     */
    @Transactional
    public int saveNewReadings(Station station, List<Reading> candidates) {
        if (candidates.isEmpty()) return 0;

        Set<Instant> timestamps = candidates.stream().map(Reading::getTimestamp).collect(Collectors.toSet());
        Set<Instant> existing = readingRepository.findExistingTimestamps(station, timestamps);

        List<Reading> toSave = candidates.stream()
                .filter(r -> !existing.contains(r.getTimestamp()))
                .collect(Collectors.toList());

        if (!toSave.isEmpty()) readingRepository.saveAll(toSave);
        return toSave.size();
    }

    /**
     * A hole longer than one and a half polling intervals is a gap. Everything that
     * fills gaps or merges feeds uses this one rule, so the cached series means the
     * same thing whoever wrote it.
     */
    public long gapThresholdSeconds(Station station) {
        long interval = Math.max(1, scheduler.getIntervalMs(station.getSourceType().name()) / 1000);
        return (long) (interval * 1.5);
    }

    public List<ReadingDto> getLast24Hours(Station station) {
        recentReads.put(station.getId(), Instant.now());
        long threshold = gapThresholdSeconds(station);
        return cache.getOrLoad(CacheKeys.last24h(station.getId(), threshold), READINGS, ttl.last24h(), () -> {
            Instant end = Instant.now();
            return loader.readings(station, end.minusSeconds(24 * 3600), end, threshold);
        });
    }

    public List<ReadingDto> getReadings(Station station, Instant start, Instant end) {
        recentReads.put(station.getId(), Instant.now());
        long threshold = gapThresholdSeconds(station);
        if (CacheSlice.withinLast24h(start, end, Instant.now())) {
            List<ReadingDto> sliced = CacheSlice.slice(
                    cache.get(CacheKeys.last24h(station.getId(), threshold), READINGS), ReadingDto::getTimestamp, start, end);
            if (sliced != null) {
                cache.recordSliceHit();
                return sliced;
            }
        }
        return cache.getOrLoad(CacheKeys.range(station.getId(), start, end, threshold), READINGS, ttl.range(),
                () -> loader.readings(station, start, end, threshold));
    }

    public List<ReadingDto> getLast24HoursForChart(Station station) {
        return downsampler.downsample(getLast24Hours(station), CHART_MAX_BUCKETS);
    }

    public List<ReadingDto> getRangeForChart(Station station, Instant start, Instant end) {
        return downsampler.downsample(getReadings(station, start, end), CHART_MAX_BUCKETS);
    }

    public List<UnifiedStationDto> getUnifiedStations() {
        unifiedReadAt = Instant.now();
        return cache.getOrLoad(CacheKeys.unifiedStations(), STATIONS, ttl.stations(), loader::unifiedStations);
    }

    /** Stations whose last 24 h someone asked for since {@code since}; the warm-up keeps only those hot. */
    public Set<UUID> stationsReadSince(Instant since) {
        recentReads.values().removeIf(t -> t.isBefore(since));
        return Set.copyOf(recentReads.keySet());
    }

    public boolean unifiedReadSince(Instant since) {
        return unifiedReadAt != null && !unifiedReadAt.isBefore(since);
    }

    public List<UnifiedStationDto> getUnifiedStationsBySource(SourceType sourceType) {
        return getUnifiedStations().stream()
                .filter(s -> s.getSourceType() == sourceType)
                .collect(Collectors.toList());
    }
}
