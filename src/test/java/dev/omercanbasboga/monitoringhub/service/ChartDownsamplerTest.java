package dev.omercanbasboga.monitoringhub.service;

import dev.omercanbasboga.monitoringhub.dto.ReadingDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChartDownsamplerTest {

    private final ChartDownsampler downsampler = new ChartDownsampler();

    @Test
    void leavesSmallSeriesUntouched() {
        List<ReadingDto> data = series(50, i -> (double) i);
        List<ReadingDto> result = downsampler.downsample(data, 300);
        assertEquals(data.size(), result.size());
    }

    @Test
    void aSpikeSurvivesDownsamplingToOnePercentOfSize() {
        int n = 30_000;
        List<ReadingDto> data = series(n, i -> 0.0);
        int spikeIndex = n / 2;
        data.get(spikeIndex).setPrimaryValue(9999.0);

        List<ReadingDto> result = downsampler.downsample(data, 300);

        assertTrue(result.size() < n / 10, "expected real downsampling to happen");
        assertTrue(result.stream().anyMatch(r -> r.getPrimaryValue() != null && r.getPrimaryValue() == 9999.0),
                "the single spike must not be averaged/dropped away");
    }

    @Test
    void firstAndLastRealPointsAreAlwaysKept() {
        List<ReadingDto> data = series(5000, i -> (double) (i % 7));
        List<ReadingDto> result = downsampler.downsample(data, 100);

        assertEquals(data.get(0).getTimestamp(), result.get(0).getTimestamp());
        assertEquals(data.get(data.size() - 1).getTimestamp(), result.get(result.size() - 1).getTimestamp());
    }

    @Test
    void gapMarkersPassThroughUntouched() {
        List<ReadingDto> data = series(10_000, i -> (double) i);
        data.add(new ReadingDto("station-1", Instant.now(), null, null)); // gap marker

        List<ReadingDto> result = downsampler.downsample(data, 300);

        assertTrue(result.stream().anyMatch(r -> r.getPrimaryValue() == null));
    }

    private List<ReadingDto> series(int n, java.util.function.IntToDoubleFunction valueAt) {
        List<ReadingDto> data = new ArrayList<>();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < n; i++) {
            data.add(new ReadingDto("station-1", start.plusSeconds(i), valueAt.applyAsDouble(i), null));
        }
        return data;
    }
}
