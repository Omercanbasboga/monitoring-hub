package dev.omercanbasboga.monitoringhub.match;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Runs the gap scan every minute over the last 15 minutes. The overlap is
 * intentional: a skipped or slow round loses nothing, and slots already
 * logged are filtered out before saving.
 */
@Component
public class MatchScanJob {

    private final FailoverService failover;
    private final Duration window;

    public MatchScanJob(FailoverService failover, @Value("${matches.scan.window:PT15M}") Duration window) {
        this.failover = failover;
        this.window = window;
    }

    @Scheduled(fixedDelayString = "${matches.scan.delay:PT1M}", initialDelayString = "${matches.scan.initial-delay:PT30S}")
    public void run() {
        failover.scan(window);
    }
}
