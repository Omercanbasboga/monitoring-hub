package dev.omercanbasboga.monitoringhub.match;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Works out which polling slots in a window had no reading.
 *
 * Slots sit on a fixed grid (multiples of the polling interval since the
 * epoch), not on the scan's own start time or on the last reading. The scan
 * runs every minute over an overlapping window, and only a fixed grid gives
 * the same instants for the same outage every time, which is what lets the
 * unique key on match_gap drop repeats.
 *
 * Readings rarely land exactly on the beat, so a hole only counts once two
 * readings are more than {@code TOLERANCE} intervals apart. Inside a real
 * hole every grid slot at least half a step away from both edges is missing.
 */
public final class GapScanner {

    static final double TOLERANCE = 1.5;

    private GapScanner() {}

    /**
     * @param readingTimes       sorted times of real readings around the window
     * @param stepSeconds        the source's polling interval
     * @param windowStart        first instant to report slots for
     * @param now                end of the scan; the last {@code tolerance} seconds are left
     *                           alone because the next poll may simply not have happened yet
     * @param watchedWholeWindow false for a match created inside the window. Then an empty
     *                           side means "too new to judge", not "down the whole time"
     */
    public static List<Instant> missingSlots(List<Instant> readingTimes, long stepSeconds,
                                             Instant windowStart, Instant now, boolean watchedWholeWindow) {
        long step = Math.max(1, stepSeconds);
        long tolerance = (long) (step * TOLERANCE);
        Instant settled = now.minusSeconds(tolerance);

        List<Instant> real = readingTimes.stream()
                .filter(t -> !t.isBefore(windowStart.minusSeconds(tolerance)))
                .toList();

        List<Instant> slots = new ArrayList<>();
        if (real.isEmpty()) {
            if (!watchedWholeWindow) return slots;
            for (Instant t = gridAtOrAfter(windowStart, step); t.isBefore(settled); t = t.plusSeconds(step)) {
                slots.add(t);
            }
            return slots;
        }

        for (int i = 1; i < real.size(); i++) {
            addHole(slots, real.get(i - 1), real.get(i), step, tolerance, windowStart);
        }
        addHole(slots, real.get(real.size() - 1), settled, step, tolerance, windowStart);
        return slots;
    }

    private static void addHole(List<Instant> slots, Instant from, Instant to, long step, long tolerance,
                                Instant windowStart) {
        if (Duration.between(from, to).getSeconds() <= tolerance) return;
        long half = step / 2;
        Instant first = gridAtOrAfter(from.plusSeconds(half), step);
        for (Instant t = first; !t.isAfter(to.minusSeconds(half)); t = t.plusSeconds(step)) {
            if (!t.isBefore(windowStart)) slots.add(t);
        }
    }

    static Instant gridAtOrAfter(Instant t, long step) {
        long s = t.getEpochSecond() + (t.getNano() > 0 ? 1 : 0);
        long aligned = Math.floorDiv(s + step - 1, step) * step;
        return Instant.ofEpochSecond(aligned);
    }
}
