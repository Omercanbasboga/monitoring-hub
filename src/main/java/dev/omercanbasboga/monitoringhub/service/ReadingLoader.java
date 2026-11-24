package dev.omercanbasboga.monitoringhub.service;

import dev.omercanbasboga.monitoringhub.dto.ReadingDto;
import dev.omercanbasboga.monitoringhub.dto.UnifiedStationDto;
import dev.omercanbasboga.monitoringhub.model.Reading;
import dev.omercanbasboga.monitoringhub.model.Station;
import dev.omercanbasboga.monitoringhub.repository.ReadingRepository;
import dev.omercanbasboga.monitoringhub.repository.StationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Database reads behind the cache. A separate bean so that callers inside
 * {@link StationDataService} and the cache warm-up go through the
 * transactional proxy; a call from within the same class would skip it.
 */
@Service
public class ReadingLoader {

    private final StationRepository stations;
    private final ReadingRepository readings;
    private final GapFiller gapFiller;

    public ReadingLoader(StationRepository stations, ReadingRepository readings, GapFiller gapFiller) {
        this.stations = stations;
        this.readings = readings;
        this.gapFiller = gapFiller;
    }

    /** Gap-filled readings for a range. */
    @Transactional(readOnly = true)
    public List<ReadingDto> readings(Station station, Instant start, Instant end, long gapThresholdSeconds) {
        List<ReadingDto> data = readings
                .findByStationAndTimestampBetweenOrderByTimestampAsc(station, start, end)
                .stream()
                .map(ReadingDto::fromEntity)
                .toList();
        return gapFiller.fillGapsWithNull(data, gapThresholdSeconds);
    }

    /** Times of real readings only, no gap markers. Used by the gap scan, which must not read the cache. */
    @Transactional(readOnly = true)
    public List<Instant> readingTimes(Station station, Instant start, Instant end) {
        return readings.findByStationAndTimestampBetweenOrderByTimestampAsc(station, start, end).stream()
                .filter(r -> r.getPrimaryValue() != null)
                .map(Reading::getTimestamp)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UnifiedStationDto> unifiedStations() {
        return stations.findAll().stream()
                .map(s -> {
                    Instant lastTs = readings.findLatestTimestamp(s);
                    Double lastVal = lastTs == null ? null :
                            readings.findByStationAndTimestampBetweenOrderByTimestampAsc(s, lastTs, lastTs)
                                    .stream().findFirst().map(Reading::getPrimaryValue).orElse(null);
                    return UnifiedStationDto.of(s, lastVal, lastTs);
                })
                .sorted(Comparator.comparing(UnifiedStationDto::getSourceType))
                .toList();
    }
}
