package dev.omercanbasboga.monitoringhub.v2;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RangeRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    @Test
    void rejectsReversedAndOverlongRanges() {
        assertThatThrownBy(() -> RangeRules.check(NOW, NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RangeRules.check(NOW.minus(Duration.ofDays(400)), NOW))
                .hasMessageContaining("366");
    }

    @Test
    void periodsResolveAgainstNow() {
        Instant[] week = RangeRules.period("weekly", NOW);
        assertThat(Duration.between(week[0], week[1])).isEqualTo(Duration.ofDays(7));
        assertThatThrownBy(() -> RangeRules.period("YEARLY", NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageRequestsAreClamped() {
        assertThat(RangeRules.page(-3, 50_000).getPageNumber()).isZero();
        assertThat(RangeRules.page(-3, 50_000).getPageSize()).isEqualTo(RangeRules.MAX_PAGE_SIZE);
        assertThat(RangeRules.page(2, 0).getPageSize()).isEqualTo(1);
    }
}
