package dev.omercanbasboga.monitoringhub.match;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GapScannerTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    private static List<Instant> beats(int fromSec, int toSec, int step) {
        List<Instant> out = new ArrayList<>();
        for (int s = fromSec; s <= toSec; s += step) out.add(T0.plusSeconds(s));
        return out;
    }

    @Test
    void steadyFeedHasNoGaps() {
        List<Instant> slots = GapScanner.missingSlots(beats(0, 900, 30), 30,
                T0, T0.plusSeconds(900), true);
        assertThat(slots).isEmpty();
    }

    @Test
    void jitterBelowToleranceIsNotAGap() {
        List<Instant> times = new ArrayList<>(beats(0, 300, 30));
        times.add(T0.plusSeconds(340)); // 40 s after the last beat, under 45 s
        times.addAll(beats(370, 900, 30));

        assertThat(GapScanner.missingSlots(times, 30, T0, T0.plusSeconds(900), true)).isEmpty();
    }

    @Test
    void everyMissedBeatInsideAHoleIsASlot() {
        List<Instant> times = new ArrayList<>(beats(0, 300, 30));
        times.addAll(beats(480, 900, 30));

        List<Instant> slots = GapScanner.missingSlots(times, 30, T0, T0.plusSeconds(900), true);

        assertThat(slots).containsExactly(
                T0.plusSeconds(330), T0.plusSeconds(360), T0.plusSeconds(390),
                T0.plusSeconds(420), T0.plusSeconds(450));
    }

    @Test
    void feedThatStoppedReportsUpToTheSettledEdgeOnly() {
        List<Instant> slots = GapScanner.missingSlots(beats(0, 600, 30), 30,
                T0, T0.plusSeconds(900), true);

        // now - 45 s = 855; slots at 630 .. 840
        assertThat(slots).first().isEqualTo(T0.plusSeconds(630));
        assertThat(slots).last().isEqualTo(T0.plusSeconds(840));
    }

    @Test
    void silentSideOfANewMatchIsNotJudged() {
        assertThat(GapScanner.missingSlots(List.of(), 30, T0, T0.plusSeconds(900), false)).isEmpty();
    }

    @Test
    void sameOutageGivesTheSameSlotsOnEveryScan() {
        // nothing at all for an hour; two scans a minute apart with sub-second start times
        Instant now1 = T0.plusSeconds(3600).plusMillis(417);
        Instant now2 = now1.plusSeconds(60);
        List<Instant> a = GapScanner.missingSlots(List.of(), 60, now1.minusSeconds(900), now1, true);
        List<Instant> b = GapScanner.missingSlots(List.of(), 60, now2.minusSeconds(900), now2, true);

        assertThat(a).allMatch(t -> t.getEpochSecond() % 60 == 0 && t.getNano() == 0);
        assertThat(b).containsAll(a.subList(1, a.size()));
    }

    @Test
    void offBeatReadingsStillGiveGridSlots() {
        List<Instant> times = List.of(T0.plusSeconds(7), T0.plusSeconds(37), T0.plusSeconds(247), T0.plusSeconds(277));
        List<Instant> slots = GapScanner.missingSlots(times, 30, T0, T0.plusSeconds(330), true);
        assertThat(slots).containsExactly(
                T0.plusSeconds(60), T0.plusSeconds(90), T0.plusSeconds(120),
                T0.plusSeconds(150), T0.plusSeconds(180), T0.plusSeconds(210));
    }

    @Test
    void silentSideOfAnOldMatchIsOneLongGap() {
        List<Instant> slots = GapScanner.missingSlots(List.of(), 60, T0, T0.plusSeconds(600), true);
        // settled edge is 600 - 90 = 510
        assertThat(slots).hasSize(9).first().isEqualTo(T0);
    }
}
