package dev.omercanbasboga.monitoringhub.cache;

import java.time.Instant;
import java.util.UUID;

/**
 * Every key the service writes, in one place. The version prefix lets a
 * deploy that changes a DTO shape start on fresh keys instead of reading
 * entries it can no longer parse.
 */
public final class CacheKeys {

    private static final String P = "mh:v1:";

    private CacheKeys() {}

    // The gap threshold is part of the key: the cached series contains gap markers,
    // and a changed polling interval must not be answered with markers for the old one.
    public static String last24h(UUID stationId, long gapThreshold) {
        return P + "station:" + stationId + ":last24h:g" + gapThreshold;
    }

    public static String range(UUID stationId, Instant start, Instant end, long gapThreshold) {
        return P + "station:" + stationId + ":range:" + start.getEpochSecond() + "-" + end.getEpochSecond()
                + ":g" + gapThreshold;
    }

    public static String unifiedStations() {
        return P + "stations";
    }

    public static String failoverLast24h(UUID matchId) {
        return P + "match:" + matchId + ":failover:last24h";
    }

    /** {@code since} should already be rounded, or every request makes a new key. */
    public static String gaps(UUID matchId, Instant since) {
        return P + "match:" + matchId + ":gaps:" + since.getEpochSecond();
    }

    public static String usage(UUID matchId) {
        return P + "match:" + matchId + ":usage";
    }
}
