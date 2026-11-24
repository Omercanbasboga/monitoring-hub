package dev.omercanbasboga.monitoringhub.match;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FailoverMergerTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");
    private static final long THRESHOLD = 45; // 30 s polling x 1.5

    /** Points every 30 s from {@code fromMin} to {@code toMin} (inclusive), value = minute. */
    private static List<SeriesPoint> every30s(int fromMin, int toMin) {
        List<SeriesPoint> out = new ArrayList<>();
        for (int s = fromMin * 60; s <= toMin * 60; s += 30) {
            out.add(new SeriesPoint(T0.plusSeconds(s), s / 60.0));
        }
        return out;
    }

    @Test
    void primaryWithoutHolesIsReturnedAsIs() {
        List<FailoverPoint> out = FailoverMerger.merge(every30s(0, 10), every30s(0, 10),
                T0, T0.plusSeconds(600), THRESHOLD);

        assertThat(out).hasSize(21).allMatch(p -> p.side() == Side.PRIMARY);
    }

    @Test
    void holeInPrimaryIsFilledFromSecondaryOnlyInsideTheHole() {
        List<SeriesPoint> primary = new ArrayList<>(every30s(0, 3));
        primary.addAll(every30s(7, 10));

        List<FailoverPoint> out = FailoverMerger.merge(primary, every30s(0, 10),
                T0, T0.plusSeconds(600), THRESHOLD);

        List<FailoverPoint> backup = out.stream().filter(p -> p.side() == Side.SECONDARY).toList();
        assertThat(backup).extracting(FailoverPoint::time)
                .first().isEqualTo(T0.plusSeconds(3 * 60 + 30));
        assertThat(backup).extracting(FailoverPoint::time)
                .last().isEqualTo(T0.plusSeconds(6 * 60 + 30));
        assertThat(backup).hasSize(7);
        assertThat(out).isSortedAccordingTo((a, b) -> a.time().compareTo(b.time()));
    }

    @Test
    void holesAtBothEndsOfTheWindowAreFilled() {
        List<FailoverPoint> out = FailoverMerger.merge(every30s(3, 6), every30s(0, 10),
                T0, T0.plusSeconds(600), THRESHOLD);

        assertThat(out.get(0).side()).isEqualTo(Side.SECONDARY);
        assertThat(out.get(0).time()).isEqualTo(T0);
        assertThat(out.get(out.size() - 1).side()).isEqualTo(Side.SECONDARY);
        assertThat(out.get(out.size() - 1).time()).isEqualTo(T0.plusSeconds(600));
    }

    @Test
    void emptyPrimaryFallsBackToSecondaryEntirely() {
        List<FailoverPoint> out = FailoverMerger.merge(List.of(), every30s(0, 5),
                T0, T0.plusSeconds(300), THRESHOLD);

        assertThat(out).hasSize(11).allMatch(p -> p.side() == Side.SECONDARY);
    }

    @Test
    void holeNobodyCoveredStaysEmptyWithoutNullMarkers() {
        List<SeriesPoint> primary = new ArrayList<>(every30s(0, 2));
        primary.addAll(every30s(8, 10));
        List<SeriesPoint> secondary = new ArrayList<>(every30s(0, 1));
        secondary.addAll(every30s(9, 10));

        List<FailoverPoint> out = FailoverMerger.merge(primary, secondary,
                T0, T0.plusSeconds(600), THRESHOLD);

        assertThat(out).allMatch(p -> p.side() == Side.PRIMARY);
        assertThat(out).hasSize(primary.size());
    }

    @Test
    void nullValuesInEitherSeriesAreIgnored() {
        List<SeriesPoint> primary = new ArrayList<>(every30s(0, 2));
        primary.add(new SeriesPoint(T0.plusSeconds(150), null));

        List<FailoverPoint> out = FailoverMerger.merge(primary, List.of(), T0, T0.plusSeconds(120), THRESHOLD);

        assertThat(out).hasSize(5);
    }
}
