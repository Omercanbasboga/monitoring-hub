package dev.omercanbasboga.monitoringhub.match;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MatchRequest(
        @NotBlank String name,
        Double latitude,
        Double longitude,
        @NotNull UUID primaryStationId,
        UUID secondaryStationId) {
}
