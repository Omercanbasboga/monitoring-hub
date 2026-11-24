package dev.omercanbasboga.monitoringhub.match;

import dev.omercanbasboga.monitoringhub.v2.RangeRules;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
@Tag(name = "Station matches", description = "Pair two sources for one gauge and read the merged series")
public class StationMatchController {

    private final StationMatchService service;
    private final FailoverService failover;

    public StationMatchController(StationMatchService service, FailoverService failover) {
        this.service = service;
        this.failover = failover;
    }

    @GetMapping
    @Operation(summary = "All matches, with the side each is currently served from")
    public List<MatchDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public MatchDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a match. Both stations are switched to active.")
    public MatchDto create(@Valid @RequestBody MatchRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a match's stations or name. There is no delete; history is kept.")
    public MatchDto update(@PathVariable UUID id, @Valid @RequestBody MatchRequest req) {
        return service.update(id, req);
    }

    @GetMapping("/{id}/series/last-24h")
    @Operation(summary = "Merged series for the last 24 h; each point says which side it came from")
    public List<FailoverPoint> last24h(@PathVariable UUID id) {
        return failover.last24h(id);
    }

    @GetMapping("/{id}/series")
    @Operation(summary = "Merged series for a range of at most 31 days")
    public List<FailoverPoint> range(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        RangeRules.checkChart(start, end);
        return failover.range(id, start, end);
    }

    @GetMapping("/{id}/gaps")
    @Operation(summary = "Missed polling slots, newest first. Defaults to the last 24 h.")
    public List<GapDto> gaps(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        return failover.gaps(id, since != null ? since : Instant.now().minus(Duration.ofHours(24)));
    }

    @GetMapping("/{id}/usage")
    @Operation(summary = "When the match switched between primary and secondary")
    public List<UsageDto> usage(@PathVariable UUID id) {
        return failover.usage(id);
    }
}
