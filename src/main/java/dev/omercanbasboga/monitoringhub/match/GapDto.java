package dev.omercanbasboga.monitoringhub.match;

import java.time.Instant;

public record GapDto(Instant slotTime, Side side) {
    static GapDto of(MatchGap g) {
        return new GapDto(g.getSlotTime(), g.getSide());
    }
}
