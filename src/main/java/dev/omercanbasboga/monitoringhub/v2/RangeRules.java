package dev.omercanbasboga.monitoringhub.v2;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Duration;
import java.time.Instant;

/**
 * Limits for the v2 history endpoints. Without them one request for "a year,
 * one page" reads millions of rows from tables that get a write every few
 * seconds.
 */
public final class RangeRules {

    public static final int MAX_PAGE_SIZE = 1000;
    public static final int MAX_RANGE_DAYS = 366;

    /** Chart and merged-series ranges load every row into memory, so they get a tighter limit. */
    public static final int MAX_CHART_DAYS = 31;

    private RangeRules() {}

    public static void check(Instant start, Instant end) {
        check(start, end, MAX_RANGE_DAYS);
    }

    public static void checkChart(Instant start, Instant end) {
        check(start, end, MAX_CHART_DAYS);
    }

    private static void check(Instant start, Instant end, int maxDays) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end are required.");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end.");
        }
        long days = Duration.between(start, end).toDays();
        if (days > maxDays) {
            throw new IllegalArgumentException(
                    "Range can be at most " + maxDays + " days, got " + days + ".");
        }
    }

    /** DAILY, WEEKLY or MONTHLY to a [start, now] pair. */
    public static Instant[] period(String period, Instant now) {
        if (period == null || period.isBlank()) {
            throw new IllegalArgumentException("period is required: DAILY, WEEKLY or MONTHLY.");
        }
        Duration back = switch (period.trim().toUpperCase()) {
            case "DAILY" -> Duration.ofDays(1);
            case "WEEKLY" -> Duration.ofDays(7);
            case "MONTHLY" -> Duration.ofDays(30);
            default -> throw new IllegalArgumentException(
                    "Unknown period " + period + ". Use DAILY, WEEKLY or MONTHLY.");
        };
        return new Instant[]{now.minus(back), now};
    }

    /** Negative pages become 0 and oversized pages are cut to {@link #MAX_PAGE_SIZE}. Newest first. */
    public static PageRequest page(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "timestamp"));
    }
}
