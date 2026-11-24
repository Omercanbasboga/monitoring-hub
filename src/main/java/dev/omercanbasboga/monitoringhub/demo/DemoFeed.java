package dev.omercanbasboga.monitoringhub.demo;

import dev.omercanbasboga.monitoringhub.match.FailoverService;
import dev.omercanbasboga.monitoringhub.match.MatchRequest;
import dev.omercanbasboga.monitoringhub.match.StationMatchRepository;
import dev.omercanbasboga.monitoringhub.match.StationMatchService;
import dev.omercanbasboga.monitoringhub.model.Reading;
import dev.omercanbasboga.monitoringhub.model.SourceType;
import dev.omercanbasboga.monitoringhub.model.Station;
import dev.omercanbasboga.monitoringhub.repository.StationRepository;
import dev.omercanbasboga.monitoringhub.service.SchedulerConfigService;
import dev.omercanbasboga.monitoringhub.service.StationDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fake feeds for running the service with nothing to connect to
 * ({@code --spring.profiles.active=demo}).
 *
 * Each gauge gets two stations on different sources, a day of tide-shaped
 * history and a few outages, so failover, the gap log and the usage history
 * all have something to show. New readings keep arriving every minute.
 */
@Component
@Profile("demo")
public class DemoFeed {

    private static final Logger log = LoggerFactory.getLogger(DemoFeed.class);
    private static final Duration STEP = Duration.ofMinutes(1);
    private static final double M2_HOURS = 12.42; // principal lunar tide

    /** name, lat, lon, primary source, backup source, mean level (m), tidal range (m) */
    private record Gauge(String name, double lat, double lon, SourceType primary, SourceType backup,
                         double mean, double amplitude, int[][] primaryOutages, int[][] backupOutages) {}

    // outages are [minutes ago start, minutes ago end]
    private static final List<Gauge> GAUGES = List.of(
            new Gauge("Valletta", 35.897, 14.512, SourceType.REST_API, SourceType.CSV_FEED, 0.42, 0.11,
                    new int[][]{{620, 540}}, new int[][]{}),
            new Gauge("Piraeus", 37.937, 23.627, SourceType.REST_API, SourceType.FTP_DELIMITED, 0.31, 0.09,
                    new int[][]{{95, 0}}, new int[][]{{900, 860}}),
            new Gauge("Split", 43.507, 16.441, SourceType.CSV_FEED, SourceType.REST_API, 0.55, 0.18,
                    new int[][]{}, new int[][]{}),
            new Gauge("Bodrum", 37.032, 27.423, SourceType.FTP_BINARY, SourceType.REST_API, 0.28, 0.08,
                    new int[][]{{1200, 1080}, {300, 270}}, new int[][]{{1150, 1120}}),
            new Gauge("Alicante", 38.338, -0.477, SourceType.REST_API, SourceType.CSV_FEED, 0.37, 0.12,
                    new int[][]{{40, 0}}, new int[][]{{40, 0}}),
            new Gauge("Naples", 40.840, 14.269, SourceType.FTP_DELIMITED, SourceType.FTP_BINARY, 0.46, 0.15,
                    new int[][]{{480, 420}}, new int[][]{}),
            new Gauge("Heraklion", 35.344, 25.137, SourceType.REST_API, null, 0.25, 0.07,
                    new int[][]{{200, 185}}, new int[][]{}),
            new Gauge("Limassol", 34.672, 33.044, SourceType.CSV_FEED, SourceType.REST_API, 0.33, 0.10,
                    new int[][]{}, new int[][]{{700, 0}}));

    private final StationRepository stations;
    private final StationMatchRepository matches;
    private final StationMatchService matchService;
    private final StationDataService data;
    private final SchedulerConfigService scheduler;
    private final FailoverService failover;
    private final Random noise = new Random(7);
    private final Instant started = Instant.now().truncatedTo(ChronoUnit.MINUTES);

    public DemoFeed(StationRepository stations, StationMatchRepository matches,
                    StationMatchService matchService, StationDataService data,
                    SchedulerConfigService scheduler, FailoverService failover) {
        this.stations = stations;
        this.matches = matches;
        this.matchService = matchService;
        this.data = data;
        this.scheduler = scheduler;
        this.failover = failover;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (matches.count() > 0) return;
        // the fake feeds tick once a minute; with the default 30 s interval every other slot would be a gap
        for (SourceType type : SourceType.values()) scheduler.updateInterval(type.name(), STEP.toMillis());
        Instant from = started.minus(Duration.ofHours(24));
        for (Gauge g : GAUGES) {
            Station p = station(g, g.primary(), "P");
            Station b = g.backup() == null ? null : station(g, g.backup(), "B");
            data.saveNewReadings(p, history(p, g, false, from, started));
            if (b != null) data.saveNewReadings(b, history(b, g, true, from, started));
            matchService.create(new MatchRequest(g.name(), g.lat(), g.lon(), p.getId(), b == null ? null : b.getId()));
        }
        // backdate the matches so the first scan judges the whole window
        matches.backdateAll(from);
        // log the gaps already in the seeded history instead of waiting a day for them
        failover.scan(Duration.ofHours(24));
        log.info("Demo data ready: {} gauges, 24 h of readings each", GAUGES.size());
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void tick() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        for (Gauge g : GAUGES) {
            append(g, g.primary(), "P", false, now);
            if (g.backup() != null) append(g, g.backup(), "B", true, now);
        }
    }

    private void append(Gauge g, SourceType type, String suffix, boolean backup, Instant now) {
        stations.findBySourceTypeAndExternalId(type, externalId(g, suffix)).ifPresent(s -> {
            if (!down(g, backup, now)) {
                data.saveNewReadings(s, List.of(new Reading(s, now, level(g, backup, now), null)));
            }
        });
    }

    private Station station(Gauge g, SourceType type, String suffix) {
        Station s = data.findOrCreateStation(type, externalId(g, suffix), g.name());
        s.setLatitude(g.lat());
        s.setLongitude(g.lon());
        s.setLocation(g.name());
        return stations.save(s);
    }

    private List<Reading> history(Station s, Gauge g, boolean backup, Instant from, Instant to) {
        List<Reading> out = new ArrayList<>();
        for (Instant t = from; !t.isAfter(to); t = t.plus(STEP)) {
            if (!down(g, backup, t)) out.add(new Reading(s, t, level(g, backup, t), null));
        }
        return out;
    }

    /** Outages are relative to when the service started, so the demo looks the same on every run. */
    private boolean down(Gauge g, boolean backup, Instant t) {
        long minsAgo = Duration.between(t, started).toMinutes();
        for (int[] o : backup ? g.backupOutages() : g.primaryOutages()) {
            if (minsAgo <= o[0] && minsAgo >= o[1]) return true;
        }
        return false;
    }

    private double level(Gauge g, boolean backup, Instant t) {
        double hours = t.getEpochSecond() / 3600.0;
        double tide = g.amplitude() * Math.sin(2 * Math.PI * hours / M2_HOURS + g.lon());
        double seiche = 0.006 * Math.sin(2 * Math.PI * hours / 0.9);
        double offset = backup ? 0.012 : 0; // two instruments never agree exactly
        return Math.round((g.mean() + tide + seiche + offset + noise.nextGaussian() * 0.0015) * 1000) / 1000.0;
    }

    private static String externalId(Gauge g, String suffix) {
        return g.name().toLowerCase().replace(' ', '-') + "-" + suffix.toLowerCase();
    }
}
