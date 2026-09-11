package dev.omercanbasboga.monitoringhub.controller;

import dev.omercanbasboga.monitoringhub.dto.UnifiedStationDto;
import dev.omercanbasboga.monitoringhub.model.SourceType;
import dev.omercanbasboga.monitoringhub.service.StationDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/unified")
@Tag(name = "Unified Stations", description = "Cross-source station view with each station's latest value")
public class UnifiedStationController {

    private final StationDataService stationDataService;

    public UnifiedStationController(StationDataService stationDataService) {
        this.stationDataService = stationDataService;
    }

    @GetMapping("/stations")
    @Operation(summary = "All stations across every configured source, with latest reading")
    public ResponseEntity<List<UnifiedStationDto>> getAllStations() {
        return ResponseEntity.ok(stationDataService.getUnifiedStations());
    }

    @GetMapping("/stations/{sourceType}")
    @Operation(summary = "Stations for one source type (REST_API | CSV_FEED | FTP_DELIMITED | FTP_BINARY)")
    public ResponseEntity<List<UnifiedStationDto>> getStationsBySource(@PathVariable SourceType sourceType) {
        return ResponseEntity.ok(stationDataService.getUnifiedStationsBySource(sourceType));
    }
}
