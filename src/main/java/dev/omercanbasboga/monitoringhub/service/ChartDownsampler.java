package dev.omercanbasboga.monitoringhub.service;

import dev.omercanbasboga.monitoringhub.dto.ReadingDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Min-max bucketing downsampler for chart endpoints. Splits the series into
 * {@code maxBuckets} equal-sized windows and keeps both the lowest and the
 * highest point from each window (a flat window keeps one point), so a spike
 * or a dip can never fall between two samples and disappear the way plain
 * every-Nth-point sampling would let it. First and last real point are always
 * kept. Gap markers (see {@link GapFiller}) are excluded from bucketing and
 * re-inserted afterwards so a real outage still renders as a visible break.
 *
 * <p>A companion project, {@code minmax-lttb-downsampler}, extracts this same
 * idea plus an LTTB pass as a standalone, framework-free library.
 *
 * <p>In the original version of this service each of four data sources had
 * its own copy-pasted implementation of this exact algorithm operating on its
 * own DTO type; this is that logic written once against the shared {@link
 * ReadingDto}.
 */
@Component
public class ChartDownsampler {

    public List<ReadingDto> downsample(List<ReadingDto> data, int maxBuckets) {
        if (data == null) return null;

        List<ReadingDto> realPoints = data.stream()
                .filter(d -> d.getPrimaryValue() != null)
                .collect(Collectors.toList());
        List<ReadingDto> gapPoints = data.stream()
                .filter(d -> d.getPrimaryValue() == null)
                .collect(Collectors.toList());

        if (realPoints.size() <= maxBuckets * 2) {
            return data;
        }

        List<ReadingDto> sampled = new ArrayList<>();
        int bucketSize = (int) Math.ceil((double) realPoints.size() / maxBuckets);
        for (int start = 0; start < realPoints.size(); start += bucketSize) {
            int end = Math.min(start + bucketSize, realPoints.size());
            ReadingDto minPoint = null;
            ReadingDto maxPoint = null;
            for (int i = start; i < end; i++) {
                ReadingDto p = realPoints.get(i);
                if (minPoint == null || p.getPrimaryValue() < minPoint.getPrimaryValue()) minPoint = p;
                if (maxPoint == null || p.getPrimaryValue() > maxPoint.getPrimaryValue()) maxPoint = p;
            }
            sampled.add(minPoint);
            if (maxPoint != minPoint) sampled.add(maxPoint);
        }

        ReadingDto firstReal = realPoints.get(0);
        ReadingDto lastReal = realPoints.get(realPoints.size() - 1);
        if (!sampled.contains(firstReal)) sampled.add(firstReal);
        if (!sampled.contains(lastReal)) sampled.add(lastReal);

        sampled.addAll(gapPoints);
        sampled.sort(Comparator.comparing(ReadingDto::getTimestamp));
        return sampled;
    }
}
