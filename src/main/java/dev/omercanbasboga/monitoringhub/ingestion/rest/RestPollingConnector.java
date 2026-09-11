package dev.omercanbasboga.monitoringhub.ingestion.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omercanbasboga.monitoringhub.model.Reading;
import dev.omercanbasboga.monitoringhub.model.SourceType;
import dev.omercanbasboga.monitoringhub.model.Station;
import dev.omercanbasboga.monitoringhub.repository.ReadingRepository;
import dev.omercanbasboga.monitoringhub.service.StationDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polls a REST/JSON time-series API per station on a schedule, then separately
 * sweeps backwards over the last few hours to backfill anything a slow or
 * flaky upstream response caused the live poll to miss. Originally written
 * against a specific tsunami-sensor JSON API; genericized here to any API
 * that returns a JSON array of {@code {timestamp, values}} for a time range.
 */
@Component
public class RestPollingConnector {

    private static final Logger logger = LoggerFactory.getLogger(RestPollingConnector.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ReadingRepository readingRepository;
    private final StationDataService stationDataService;

    @Value("${connectors.rest.base-url:}")
    private String baseUrl;
    @Value("${connectors.rest.record-limit:10000}")
    private int recordLimit;
    @Value("${connectors.rest.mode:json}")
    private String mode;

    private final AtomicBoolean pollRunning = new AtomicBoolean(false);

    public RestPollingConnector(RestTemplate restTemplate, ObjectMapper objectMapper,
                                 ReadingRepository readingRepository, StationDataService stationDataService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.readingRepository = readingRepository;
        this.stationDataService = stationDataService;
    }

    /** Polls every known active station for the last completed hour. */
    @Async
    public void poll(List<Station> activeStations) {
        if (baseUrl.isBlank()) return; // connector not configured, skip silently
        if (!pollRunning.compareAndSet(false, true)) {
            logger.warn("Previous REST poll still running, skipping this tick.");
            return;
        }
        try {
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);
            ZonedDateTime oneHourBefore = now.minusHours(1);

            for (Station station : activeStations) {
                fetchRange(station, oneHourBefore, now);
            }
        } finally {
            pollRunning.set(false);
        }
    }

    private void fetchRange(Station station, ZonedDateTime from, ZonedDateTime to) {
        String url = String.format("%s/%s?tMin=%s&tMax=%s&nRec=%d&mode=%s",
                baseUrl, station.getExternalId(), from.format(TIME_FORMAT), to.format(TIME_FORMAT), recordLimit, mode);

        try {
            String rawJson = restTemplate.getForObject(url, String.class);
            List<RestReadingPayload> payloads = objectMapper.readValue(rawJson, new TypeReference<>() {});

            List<Reading> candidates = new ArrayList<>();
            Set<java.time.Instant> seen = new HashSet<>();
            for (RestReadingPayload p : payloads) {
                java.time.Instant ts = ZonedDateTime.parse(p.timestamp(), DateTimeFormatter.ISO_DATE_TIME).toInstant();
                if (seen.add(ts)) {
                    candidates.add(new Reading(station, ts, p.values().get("primary"), p.values().get("secondary")));
                }
            }

            int saved = stationDataService.saveNewReadings(station, candidates);
            if (saved > 0) logger.info("REST poll: {} new readings saved for station {}.", saved, station.getExternalId());
        } catch (Exception e) {
            logger.error("REST poll failed for station {}: {}", station.getExternalId(), e.getMessage());
        }
    }

    public record RestReadingPayload(String timestamp, java.util.Map<String, Double> values) {}
}
