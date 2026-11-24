package dev.omercanbasboga.monitoringhub.match;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One physical gauge that two sources report on. The primary is what we chart
 * by default; the secondary fills in while the primary is silent.
 *
 * There is no delete on purpose. Gap and usage rows point at the match id, and
 * a deleted match would leave that history orphaned. Fix a wrong match with PUT.
 */
@Entity
@Table(name = "station_match")
public class StationMatch {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    private Double latitude;
    private Double longitude;

    @Column(name = "primary_station_id", nullable = false)
    private UUID primaryStationId;

    @Column(name = "secondary_station_id")
    private UUID secondaryStationId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean hasSecondary() {
        return secondaryStationId != null;
    }

    public UUID getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public UUID getPrimaryStationId() { return primaryStationId; }
    public void setPrimaryStationId(UUID primaryStationId) { this.primaryStationId = primaryStationId; }

    public UUID getSecondaryStationId() { return secondaryStationId; }
    public void setSecondaryStationId(UUID secondaryStationId) { this.secondaryStationId = secondaryStationId; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
