package dev.omercanbasboga.monitoringhub.match;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.omercanbasboga.monitoringhub.cache.CacheKeys;
import dev.omercanbasboga.monitoringhub.cache.CacheService;
import dev.omercanbasboga.monitoringhub.cache.CacheTtl;
import dev.omercanbasboga.monitoringhub.model.Station;
import dev.omercanbasboga.monitoringhub.repository.StationRepository;
import dev.omercanbasboga.monitoringhub.service.ReadingLoader;
import dev.omercanbasboga.monitoringhub.service.SchedulerConfigService;
import dev.omercanbasboga.monitoringhub.service.StationDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read side of a match (the merged series, its gap log and which side served
 * it when) plus the periodic scan that writes that log.
 */
@Service
public class FailoverService {

    private static final Logger log = LoggerFactory.getLogger(FailoverService.class);

    private static final TypeReference<List<FailoverPoint>> POINTS = new TypeReference<>() {};
    private static final TypeReference<List<GapDto>> GAPS = new TypeReference<>() {};
    private static final TypeReference<List<UsageDto>> USAGE = new TypeReference<>() {};

    private final StationMatchRepository matches;
    private final StationRepository stations;
    private final MatchGapRepository gaps;
    private final SourceUsageRepository usage;
    private final StationDataService data;
    private final ReadingLoader loader;
    private final SchedulerConfigService scheduler;
    private final CacheService cache;
    private final CacheTtl ttl;

    public FailoverService(StationMatchRepository matches, StationRepository stations, MatchGapRepository gaps,
                           SourceUsageRepository usage, StationDataService data, ReadingLoader loader,
                           SchedulerConfigService scheduler, CacheService cache, CacheTtl ttl) {
        this.matches = matches;
        this.stations = stations;
        this.gaps = gaps;
        this.usage = usage;
        this.data = data;
        this.loader = loader;
        this.scheduler = scheduler;
        this.cache = cache;
        this.ttl = ttl;
    }

    public List<FailoverPoint> last24h(UUID matchId) {
        return cache.getOrLoad(CacheKeys.failoverLast24h(matchId), POINTS, ttl.last24h(), () -> {
            Instant end = Instant.now();
            return merged(matchId, end.minus(Duration.ofHours(24)), end);
        });
    }

    /** Not cached as a whole: each side's range read already uses the cache and the 24 h slice. */
    public List<FailoverPoint> range(UUID matchId, Instant start, Instant end) {
        return merged(matchId, start, end);
    }

    /**
     * The cached list starts at {@code since} rounded down to the hour (otherwise "last 24 h"
     * is a new key every second) and is cut to the exact {@code since} afterwards. New gaps
     * show up when the entry expires, within {@code cache.ttl.history}.
     */
    public List<GapDto> gaps(UUID matchId, Instant since) {
        Instant hour = since.truncatedTo(ChronoUnit.HOURS);
        return cache.getOrLoad(CacheKeys.gaps(matchId, hour), GAPS, ttl.history(), () ->
                        gaps.findByMatchIdAndSlotTimeGreaterThanEqualOrderBySlotTimeDesc(matchId, hour)
                                .stream().map(GapDto::of).toList())
                .stream()
                .filter(g -> !g.slotTime().isBefore(since))
                .toList();
    }

    public List<UsageDto> usage(UUID matchId) {
        return cache.getOrLoad(CacheKeys.usage(matchId), USAGE, ttl.history(), () ->
                usage.findByMatchIdOrderByStartedAtDesc(matchId).stream().map(UsageDto::of).toList());
    }

    public Optional<Side> activeSide(UUID matchId) {
        return usage.findFirstByMatchIdAndEndedAtIsNullOrderByStartedAtDesc(matchId).map(SourceUsage::getSide);
    }

    private List<FailoverPoint> merged(UUID matchId, Instant start, Instant end) {
        StationMatch m = matches.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown match: " + matchId));
        Station primary = station(m.getPrimaryStationId());
        Station secondary = m.hasSecondary() ? station(m.getSecondaryStationId()) : null;

        List<SeriesPoint> p = series(primary, start, end);
        List<SeriesPoint> s = secondary == null ? List.of() : series(secondary, start, end);
        return FailoverMerger.merge(p, s, start, end, data.gapThresholdSeconds(primary));
    }

    // ---- scan -------------------------------------------------------

    /**
     * Logs missed slots for both sides of every match and records a switch of
     * active side.
     *
     * Deliberately not one transaction. A long round holding one connection
     * and rolling back on the first bad match is worse than each save
     * committing on its own; a failure is logged and the next match goes on.
     */
    public void scan(Duration window) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(window);

        for (StationMatch m : matches.findAll()) {
            try {
                Station primary = station(m.getPrimaryStationId());
                Station secondary = m.hasSecondary() ? station(m.getSecondaryStationId()) : null;
                boolean watchedWholeWindow = m.getCreatedAt() != null && m.getCreatedAt().isBefore(windowStart);

                List<Instant> pTimes = readingTimes(primary, windowStart, now);
                logGaps(m, Side.PRIMARY, primary, pTimes, windowStart, now, watchedWholeWindow);

                List<Instant> sTimes = List.of();
                if (secondary != null) {
                    sTimes = readingTimes(secondary, windowStart, now);
                    logGaps(m, Side.SECONDARY, secondary, sTimes, windowStart, now, watchedWholeWindow);
                }
                recordActiveSide(m, primary, pTimes, secondary, sTimes, now);
            } catch (Exception e) {
                log.warn("Scan failed for match {}: {}", m.getName(), e.getMessage());
            }
        }
    }

    private void logGaps(StationMatch m, Side side, Station station, List<Instant> times,
                         Instant windowStart, Instant now, boolean watchedWholeWindow) {
        long step = intervalSeconds(station);
        List<Instant> missing = GapScanner.missingSlots(times, step, windowStart, now, watchedWholeWindow);
        if (missing.isEmpty()) return;

        long tolerance = (long) (step * GapScanner.TOLERANCE);
        Set<Instant> known = new HashSet<>(gaps.slotsBetween(m.getId(), side, windowStart.minusSeconds(tolerance), now));
        List<MatchGap> fresh = missing.stream()
                .filter(known::add)
                .map(t -> new MatchGap(m.getId(), side, station.getId(), t))
                .toList();
        if (fresh.isEmpty()) return;

        gaps.saveAll(fresh);
        log.debug("{} / {}: {} new missed slots", m.getName(), side, fresh.size());
    }

    private void recordActiveSide(StationMatch m, Station primary, List<Instant> pTimes,
                                  Station secondary, List<Instant> sTimes, Instant now) {
        Side side;
        if (isFresh(pTimes, now, data.gapThresholdSeconds(primary))) side = Side.PRIMARY;
        else if (secondary != null && isFresh(sTimes, now, data.gapThresholdSeconds(secondary))) side = Side.SECONDARY;
        else side = Side.NONE;

        Optional<SourceUsage> open = usage.findFirstByMatchIdAndEndedAtIsNullOrderByStartedAtDesc(m.getId());
        if (open.isPresent() && open.get().getSide() == side) return;

        open.ifPresent(u -> {
            u.setEndedAt(now);
            usage.save(u);
        });
        usage.save(new SourceUsage(m.getId(), side, now));
        cache.evict(CacheKeys.usage(m.getId()));
        log.info("{} is now served from {}", m.getName(), side);
    }

    // ---- helpers ----------------------------------------------------

    private List<SeriesPoint> series(Station s, Instant start, Instant end) {
        return data.getReadings(s, start, end).stream()
                .filter(r -> r.getPrimaryValue() != null)
                .map(r -> new SeriesPoint(r.getTimestamp(), r.getPrimaryValue()))
                .toList();
    }

    /**
     * Straight from the database, never the cache. A cached series can be a couple
     * of minutes old, and the gap log is written once and never corrected, so a
     * stale read would leave permanent false gaps. The extra hour before the window
     * finds the reading a hole started from.
     */
    private List<Instant> readingTimes(Station s, Instant windowStart, Instant now) {
        return loader.readingTimes(s, windowStart.minus(Duration.ofHours(1)), now).stream()
                .map(t -> t.truncatedTo(ChronoUnit.SECONDS))
                .toList();
    }

    private static boolean isFresh(List<Instant> times, Instant now, long thresholdSeconds) {
        return !times.isEmpty()
                && Duration.between(times.get(times.size() - 1), now).getSeconds() <= thresholdSeconds;
    }

    private long intervalSeconds(Station s) {
        return Math.max(1, scheduler.getIntervalMs(s.getSourceType().name()) / 1000);
    }

    private Station station(UUID id) {
        return stations.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown station: " + id));
    }
}
