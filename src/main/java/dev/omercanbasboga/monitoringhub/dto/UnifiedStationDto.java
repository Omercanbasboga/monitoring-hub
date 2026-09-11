package dev.omercanbasboga.monitoringhub.dto;

import dev.omercanbasboga.monitoringhub.model.SourceType;
import dev.omercanbasboga.monitoringhub.model.Station;

import java.time.Instant;

public class UnifiedStationDto {

    private String id;
    private SourceType sourceType;
    private String externalId;
    private String name;
    private String location;
    private String country;
    private Double latitude;
    private Double longitude;
    private Double lastValue;
    private Instant lastReadingAt;

    public static UnifiedStationDto of(Station s, Double lastValue, Instant lastReadingAt) {
        UnifiedStationDto dto = new UnifiedStationDto();
        dto.id = s.getId().toString();
        dto.sourceType = s.getSourceType();
        dto.externalId = s.getExternalId();
        dto.name = s.getName();
        dto.location = s.getLocation();
        dto.country = s.getCountry();
        dto.latitude = s.getLatitude();
        dto.longitude = s.getLongitude();
        dto.lastValue = lastValue;
        dto.lastReadingAt = lastReadingAt;
        return dto;
    }

    public String getId() { return id; }
    public SourceType getSourceType() { return sourceType; }
    public String getExternalId() { return externalId; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public String getCountry() { return country; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getLastValue() { return lastValue; }
    public Instant getLastReadingAt() { return lastReadingAt; }
}
