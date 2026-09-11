package dev.omercanbasboga.monitoringhub.controller;

import dev.omercanbasboga.monitoringhub.dto.ReadingDto;
import dev.omercanbasboga.monitoringhub.model.SourceType;
import dev.omercanbasboga.monitoringhub.model.Station;
import dev.omercanbasboga.monitoringhub.repository.StationRepository;
import dev.omercanbasboga.monitoringhub.service.StationDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/sources/{sourceType}/stations/{externalId}/readings")
@Tag(name = "Station Readings", description = "Raw and chart-ready (gap-filled + downsampled) readings for one station")
public class StationDataController {

    private static final long DEFAULT_GAP_THRESHOLD_SECONDS = 90;

    private final StationDataService stationDataService;
    private final StationRepository stationRepository;

    public StationDataController(StationDataService stationDataService, StationRepository stationRepository) {
        this.stationDataService = stationDataService;
        this.stationRepository = stationRepository;
    }

    @GetMapping
    @Operation(summary = "Raw readings for a time range (gap-filled, not downsampled)")
    public ResponseEntity<List<ReadingDto>> getRange(
            @PathVariable SourceType sourceType, @PathVariable String externalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        Station station = requireStation(sourceType, externalId);
        return ResponseEntity.ok(stationDataService.getReadings(station, start, end, DEFAULT_GAP_THRESHOLD_SECONDS));
    }

    @GetMapping("/chart/last-24h")
    @Operation(summary = "Last 24h, gap-filled and downsampled for charting")
    public ResponseEntity<List<ReadingDto>> getLast24hForChart(
            @PathVariable SourceType sourceType, @PathVariable String externalId) {
        Station station = requireStation(sourceType, externalId);
        return ResponseEntity.ok(stationDataService.getLast24HoursForChart(station, DEFAULT_GAP_THRESHOLD_SECONDS));
    }

    @GetMapping("/chart")
    @Operation(summary = "Arbitrary time range, gap-filled and downsampled for charting")
    public ResponseEntity<List<ReadingDto>> getRangeForChart(
            @PathVariable SourceType sourceType, @PathVariable String externalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        Station station = requireStation(sourceType, externalId);
        return ResponseEntity.ok(stationDataService.getRangeForChart(station, start, end, DEFAULT_GAP_THRESHOLD_SECONDS));
    }

    private Station requireStation(SourceType sourceType, String externalId) {
        return stationRepository.findBySourceTypeAndExternalId(sourceType, externalId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown station: " + sourceType + "/" + externalId));
    }
}
