package dev.omercanbasboga.monitoringhub.service;

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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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

    private final StationRepository stationRepository;
    private final ReadingRepository readingRepository;
    private final GapFiller gapFiller;
    private final ChartDownsampler downsampler;

    public StationDataService(StationRepository stationRepository, ReadingRepository readingRepository,
                               GapFiller gapFiller, ChartDownsampler downsampler) {
        this.stationRepository = stationRepository;
        this.readingRepository = readingRepository;
        this.gapFiller = gapFiller;
        this.downsampler = downsampler;
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

    @Transactional(readOnly = true)
    public List<ReadingDto> getReadings(Station station, Instant start, Instant end, long gapThresholdSeconds) {
        List<ReadingDto> data = readingRepository
                .findByStationAndTimestampBetweenOrderByTimestampAsc(station, start, end)
                .stream().map(ReadingDto::fromEntity).collect(Collectors.toList());
        return gapFiller.fillGapsWithNull(data, gapThresholdSeconds);
    }

    public List<ReadingDto> getLast24HoursForChart(Station station, long gapThresholdSeconds) {
        Instant end = Instant.now();
        List<ReadingDto> data = getReadings(station, end.minusSeconds(24 * 3600), end, gapThresholdSeconds);
        return downsampler.downsample(data, CHART_MAX_BUCKETS);
    }

    public List<ReadingDto> getRangeForChart(Station station, Instant start, Instant end, long gapThresholdSeconds) {
        List<ReadingDto> data = getReadings(station, start, end, gapThresholdSeconds);
        return downsampler.downsample(data, CHART_MAX_BUCKETS);
    }

    @Transactional(readOnly = true)
    public List<UnifiedStationDto> getUnifiedStations() {
        return stationRepository.findAll().stream()
                .map(s -> {
                    Instant lastTs = readingRepository.findLatestTimestamp(s);
                    Double lastVal = lastTs == null ? null :
                            readingRepository.findByStationAndTimestampBetweenOrderByTimestampAsc(s, lastTs, lastTs)
                                    .stream().findFirst().map(Reading::getPrimaryValue).orElse(null);
                    return UnifiedStationDto.of(s, lastVal, lastTs);
                })
                .sorted(Comparator.comparing(UnifiedStationDto::getSourceType))
                .collect(Collectors.toList());
    }

    public List<UnifiedStationDto> getUnifiedStationsBySource(SourceType sourceType) {
        return getUnifiedStations().stream()
                .filter(s -> s.getSourceType() == sourceType)
                .collect(Collectors.toList());
    }
}
