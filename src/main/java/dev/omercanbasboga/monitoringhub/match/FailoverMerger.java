package dev.omercanbasboga.monitoringhub.match;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Builds one series out of a primary and a backup source.
 *
 * The primary is taken as-is. Wherever two consecutive primary points are
 * further apart than {@code thresholdSeconds}, the secondary's points from
 * inside that hole are spliced in. Holes at the start and end of the window
 * count too, and a window with no primary data at all is served entirely from
 * the secondary.
 *
 * Nothing is stored; the merge runs at read time. It also never emits null
 * markers for holes both sides missed. An earlier version did, and with
 * incremental fetching on the client those markers piled up as permanent
 * broken segments. The client already draws a break wherever time jumps.
 */
public final class FailoverMerger {

    private FailoverMerger() {}

    public static List<FailoverPoint> merge(List<SeriesPoint> primary, List<SeriesPoint> secondary,
                                            Instant windowStart, Instant windowEnd, long thresholdSeconds) {
        List<SeriesPoint> p = primary.stream()
                .filter(x -> x.time() != null && x.value() != null)
                .sorted(Comparator.comparing(SeriesPoint::time))
                .toList();

        TreeMap<Instant, Double> backup = new TreeMap<>();
        for (SeriesPoint x : secondary) {
            if (x.time() != null && x.value() != null) backup.put(x.time(), x.value());
        }

        List<FailoverPoint> out = new ArrayList<>();
        if (p.isEmpty()) {
            splice(out, backup.subMap(windowStart, true, windowEnd, true));
            return out;
        }

        Instant first = p.get(0).time();
        if (seconds(windowStart, first) > thresholdSeconds) {
            splice(out, backup.subMap(windowStart, true, first, false));
        }

        Instant prev = null;
        for (SeriesPoint pt : p) {
            if (prev != null && seconds(prev, pt.time()) > thresholdSeconds) {
                splice(out, backup.subMap(prev, false, pt.time(), false));
            }
            out.add(new FailoverPoint(pt.time(), pt.value(), Side.PRIMARY));
            prev = pt.time();
        }

        if (seconds(prev, windowEnd) > thresholdSeconds) {
            splice(out, backup.subMap(prev, false, windowEnd, true));
        }
        return out;
    }

    private static void splice(List<FailoverPoint> out, NavigableMap<Instant, Double> points) {
        points.forEach((t, v) -> out.add(new FailoverPoint(t, v, Side.SECONDARY)));
    }

    private static long seconds(Instant a, Instant b) {
        return Duration.between(a, b).getSeconds();
    }
}
