package dev.omercanbasboga.monitoringhub.controller;

import dev.omercanbasboga.monitoringhub.service.SchedulerConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/scheduler")
@Tag(name = "Scheduler Config", description = "Runtime-tunable per-source polling intervals")
public class SchedulerConfigController {

    private final SchedulerConfigService schedulerConfigService;

    public SchedulerConfigController(SchedulerConfigService schedulerConfigService) {
        this.schedulerConfigService = schedulerConfigService;
    }

    @GetMapping("/intervals")
    @Operation(summary = "Current polling interval (ms) for every source")
    public ResponseEntity<Map<String, Long>> getIntervals() {
        return ResponseEntity.ok(schedulerConfigService.getAllIntervals());
    }

    @PutMapping("/intervals/{source}")
    @Operation(summary = "Update a source's polling interval; takes effect on its next tick, no restart needed")
    public ResponseEntity<Long> updateInterval(@PathVariable String source, @RequestParam long intervalMs) {
        return ResponseEntity.ok(schedulerConfigService.updateInterval(source, intervalMs));
    }
}
