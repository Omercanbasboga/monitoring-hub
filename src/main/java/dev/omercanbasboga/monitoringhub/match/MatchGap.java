package dev.omercanbasboga.monitoringhub.match;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A slot where one side of a match should have sent a reading and didn't.
 * The slot length is that source's polling interval, so a source polled every
 * 30 s that goes quiet for three minutes leaves six rows.
 *
 * Rows are never deleted or rewritten. If data arrives late, the slot was still
 * empty at the time we looked, and that is what the history should say.
 */
@Entity
@Table(name = "match_gap",
        uniqueConstraints = @UniqueConstraint(name = "uq_match_gap_slot",
                columnNames = {"match_id", "side", "slot_time"}),
        indexes = @Index(name = "idx_match_gap_match_slot", columnList = "match_id, slot_time"))
public class MatchGap {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Side side;

    @Column(name = "station_id", nullable = false)
    private UUID stationId;

    @Column(name = "slot_time", nullable = false)
    private Instant slotTime;

    protected MatchGap() {}

    public MatchGap(UUID matchId, Side side, UUID stationId, Instant slotTime) {
        this.matchId = matchId;
        this.side = side;
        this.stationId = stationId;
        this.slotTime = slotTime;
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public Side getSide() { return side; }
    public UUID getStationId() { return stationId; }
    public Instant getSlotTime() { return slotTime; }
}
