package dev.omercanbasboga.monitoringhub.cache;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Answers a range request from the cached 24 h series when the range fits
 * inside it.
 *
 * Range endpoints take free start/end parameters, so caching each range under
 * its own key would never hit. But most ranges people ask for are within the
 * last day, and that series is already warm in Redis. Filtering it in memory
 * is cheaper than any query.
 */
public final class CacheSlice {

    private CacheSlice() {}

    /** Items with {@code start <= time <= end}; null if there is no warm series to slice. */
    public static <T> List<T> slice(List<T> series, Function<T, Instant> timeOf, Instant start, Instant end) {
        if (series == null) return null;
        return series.stream()
                .filter(item -> {
                    Instant t = timeOf.apply(item);
                    return t != null && !t.isBefore(start) && !t.isAfter(end);
                })
                .toList();
    }

    /** True when [start, end] lies inside the last 24 h ending at {@code now}. */
    public static boolean withinLast24h(Instant start, Instant end, Instant now) {
        return !start.isBefore(now.minusSeconds(24 * 3600)) && !end.isAfter(now);
    }
}
