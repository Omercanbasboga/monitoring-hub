package dev.omercanbasboga.monitoringhub.match;

import java.time.Instant;

public record UsageDto(Side side, Instant startedAt, Instant endedAt) {
    static UsageDto of(SourceUsage u) {
        return new UsageDto(u.getSide(), u.getStartedAt(), u.getEndedAt());
    }
}
