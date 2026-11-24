package dev.omercanbasboga.monitoringhub.match;

import java.time.Instant;

/** A point of the merged series, tagged with the side it came from so the chart can colour it. */
public record FailoverPoint(Instant time, double value, Side side) {
}
