package dev.omercanbasboga.monitoringhub.match;

import dev.omercanbasboga.monitoringhub.model.SourceType;
import dev.omercanbasboga.monitoringhub.model.Station;

import java.time.Instant;
import java.util.UUID;

public record MatchDto(
        UUID id,
        String name,
        Double latitude,
        Double longitude,
        StationRef primary,
        StationRef secondary,
        Side activeSide,
        Instant createdAt) {

    public record StationRef(UUID id, SourceType sourceType, String externalId, String name) {
        static StationRef of(Station s) {
            return s == null ? null : new StationRef(s.getId(), s.getSourceType(), s.getExternalId(), s.getName());
        }
    }

    static MatchDto of(StationMatch m, Station primary, Station secondary, Side activeSide) {
        return new MatchDto(m.getId(), m.getName(), m.getLatitude(), m.getLongitude(),
                StationRef.of(primary), StationRef.of(secondary), activeSide, m.getCreatedAt());
    }
}
