package dev.omercanbasboga.monitoringhub.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One timestamped measurement. {@code primaryValue} is what gets charted
 * (water level, for example); {@code secondaryValue} is an optional second
 * channel some feeds send in the same row.
 */
@Entity
@Table(name = "reading",
        uniqueConstraints = @UniqueConstraint(name = "uq_reading_station_ts", columnNames = {"station_id", "ts"}),
        indexes = @Index(name = "idx_reading_station_ts", columnList = "station_id, ts"))
public class Reading {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "ts", nullable = false)
    private Instant timestamp;

    @Column(name = "primary_value")
    private Double primaryValue;

    @Column(name = "secondary_value")
    private Double secondaryValue;

    protected Reading() {}

    public Reading(Station station, Instant timestamp, Double primaryValue, Double secondaryValue) {
        this.station = station;
        this.timestamp = timestamp;
        this.primaryValue = primaryValue;
        this.secondaryValue = secondaryValue;
    }

    public UUID getId() { return id; }
    public Station getStation() { return station; }
    public Instant getTimestamp() { return timestamp; }
    public Double getPrimaryValue() { return primaryValue; }
    public Double getSecondaryValue() { return secondaryValue; }
}
