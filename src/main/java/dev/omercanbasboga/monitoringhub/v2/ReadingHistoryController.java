package dev.omercanbasboga.monitoringhub.v2;

import dev.omercanbasboga.monitoringhub.dto.ReadingDto;
import dev.omercanbasboga.monitoringhub.model.SourceType;
import dev.omercanbasboga.monitoringhub.model.Station;
import dev.omercanbasboga.monitoringhub.repository.ReadingRepository;
import dev.omercanbasboga.monitoringhub.repository.StationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Paged history for one station. Unlike the chart endpoints these return raw
 * rows, newest first, and read the database directly: old ranges are asked
 * for rarely and would only crowd the cache.
 */
@RestController
@RequestMapping("/api/v2/sources/{sourceType}/stations/{externalId}/readings")
@Tag(name = "v2 history", description = "Paged raw readings, newest first")
public class ReadingHistoryController {

    private final StationRepository stations;
    private final ReadingRepository readings;

    public ReadingHistoryController(StationRepository stations, ReadingRepository readings) {
        this.stations = stations;
        this.readings = readings;
    }

    @GetMapping
    @Operation(summary = "Readings in a range, at most 366 days and 1000 rows per page")
    @Transactional(readOnly = true)
    public PagedResponse<ReadingDto> range(
            @PathVariable SourceType sourceType, @PathVariable String externalId,
            @Parameter(example = "2026-11-01T00:00:00Z")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @Parameter(example = "2026-11-08T00:00:00Z")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        RangeRules.check(start, end);
        return query(sourceType, externalId, start, end, page, size);
    }

    @GetMapping("/period")
    @Operation(summary = "Readings for DAILY (24 h), WEEKLY (7 d) or MONTHLY (30 d)")
    @Transactional(readOnly = true)
    public PagedResponse<ReadingDto> period(
            @PathVariable SourceType sourceType, @PathVariable String externalId,
            @Parameter(example = "WEEKLY") @RequestParam String period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Instant[] r = RangeRules.period(period, Instant.now());
        return query(sourceType, externalId, r[0], r[1], page, size);
    }

    private PagedResponse<ReadingDto> query(SourceType sourceType, String externalId, Instant start, Instant end,
                                            int page, int size) {
        Station station = stations.findBySourceTypeAndExternalId(sourceType, externalId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown station: " + sourceType + "/" + externalId));
        return PagedResponse.of(readings
                .findByStationAndTimestampBetween(station, start, end, RangeRules.page(page, size))
                .map(r -> new ReadingDto(externalId, r.getTimestamp(), r.getPrimaryValue(), r.getSecondaryValue())));
    }
}
