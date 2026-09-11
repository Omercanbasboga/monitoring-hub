package dev.omercanbasboga.monitoringhub.service;

import dev.omercanbasboga.monitoringhub.dto.ReadingDto;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Inserts a synthetic null-value point halfway through any gap between two
 * consecutive readings wider than {@code gapThresholdSeconds}, so a chart
 * renders a visible break for a real sensor/network outage instead of a
 * misleadingly straight interpolated line across it.
 */
@Component
public class GapFiller {

    public List<ReadingDto> fillGapsWithNull(List<ReadingDto> data, long gapThresholdSeconds) {
        if (data == null || data.size() < 2) return data;

        List<ReadingDto> sorted = data.stream()
                .sorted(Comparator.comparing(ReadingDto::getTimestamp))
                .collect(Collectors.toList());

        List<ReadingDto> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            result.add(sorted.get(i));

            if (i < sorted.size() - 1) {
                Instant current = sorted.get(i).getTimestamp();
                Instant next = sorted.get(i + 1).getTimestamp();
                long diffSeconds = Duration.between(current, next).getSeconds();

                if (diffSeconds > gapThresholdSeconds) {
                    Instant gapTime = current.plusSeconds(diffSeconds / 2);
                    result.add(new ReadingDto(sorted.get(i).getStationExternalId(), gapTime, null, null));
                }
            }
        }
        return result;
    }
}
