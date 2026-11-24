package dev.omercanbasboga.monitoringhub.match;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Which side a match was being served from, and for how long. The open row
 * (no end time) is the current state; a switch closes it and opens a new one.
 */
@Entity
@Table(name = "source_usage", indexes = @Index(name = "idx_source_usage_match", columnList = "match_id, started_at"))
public class SourceUsage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Side side;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected SourceUsage() {}

    public SourceUsage(UUID matchId, Side side, Instant startedAt) {
        this.matchId = matchId;
        this.side = side;
        this.startedAt = startedAt;
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public Side getSide() { return side; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
}
