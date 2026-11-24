package dev.omercanbasboga.monitoringhub.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * TTLs. Keep the warmed ones (last24h, stations) well above the warm-up
 * period: if a warm-up round runs long and the TTL is tight, keys expire
 * between rounds and every request lands on the database.
 */
@ConfigurationProperties(prefix = "cache.ttl")
public record CacheTtl(Duration last24h, Duration range, Duration stations, Duration history) {

    public CacheTtl {
        if (last24h == null) last24h = Duration.ofMinutes(2);
        if (range == null) range = Duration.ofMinutes(1);
        if (stations == null) stations = Duration.ofMinutes(2);
        if (history == null) history = Duration.ofMinutes(2);
    }
}
