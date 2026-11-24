package dev.omercanbasboga.monitoringhub.cache;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class CacheSliceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    @Test
    void keepsBothEnds() {
        List<Instant> series = List.of(NOW.minusSeconds(300), NOW.minusSeconds(200), NOW.minusSeconds(100), NOW);
        List<Instant> out = CacheSlice.slice(series, Function.identity(), NOW.minusSeconds(200), NOW.minusSeconds(100));
        assertThat(out).containsExactly(NOW.minusSeconds(200), NOW.minusSeconds(100));
    }

    @Test
    void coldSeriesGivesNullSoTheCallerGoesToTheDatabase() {
        assertThat(CacheSlice.slice(null, Function.<Instant>identity(), NOW.minusSeconds(60), NOW)).isNull();
    }

    @Test
    void onlyRangesInsideTheLastDayAreSliceable() {
        assertThat(CacheSlice.withinLast24h(NOW.minusSeconds(3600), NOW, NOW)).isTrue();
        assertThat(CacheSlice.withinLast24h(NOW.minusSeconds(25 * 3600), NOW, NOW)).isFalse();
        assertThat(CacheSlice.withinLast24h(NOW.minusSeconds(60), NOW.plusSeconds(60), NOW)).isFalse();
    }
}
