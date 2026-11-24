package dev.omercanbasboga.monitoringhub.model;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * A measuring point as one source knows it. The same physical gauge can show
 * up under two sources; {@link dev.omercanbasboga.monitoringhub.match.StationMatch}
 * ties those together.
 */
@Entity
@Table(name = "station", uniqueConstraints = @UniqueConstraint(
        name = "uq_station_source_external", columnNames = {"source_type", "external_id"}))
public class Station {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private SourceType sourceType;

    /** The id the source itself uses (sensor id, file prefix, column header...). */
    @Column(name = "external_id", nullable = false)
    private String externalId;

    private String name;
    private String location;
    private String country;
    private Double latitude;
    private Double longitude;

    /** Only active stations are polled by the REST connector. */
    @Column(nullable = false)
    private boolean active = true;

    protected Station() {}

    public Station(SourceType sourceType, String externalId, String name) {
        this.sourceType = sourceType;
        this.externalId = externalId;
        this.name = name;
    }

    public UUID getId() { return id; }
    public SourceType getSourceType() { return sourceType; }
    public String getExternalId() { return externalId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
