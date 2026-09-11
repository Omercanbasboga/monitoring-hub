package dev.omercanbasboga.monitoringhub.dto;

import dev.omercanbasboga.monitoringhub.model.Reading;

import java.time.Instant;

/**
 * {@code primaryValue == null} marks a synthetic gap point inserted by {@link
 * dev.omercanbasboga.monitoringhub.service.GapFiller} so a chart renders a visible break
 * instead of drawing a straight line across a real outage.
 */
public class ReadingDto {

    private String stationExternalId;
    private Instant timestamp;
    private Double primaryValue;
    private Double secondaryValue;

    public ReadingDto() {}

    public ReadingDto(String stationExternalId, Instant timestamp, Double primaryValue, Double secondaryValue) {
        this.stationExternalId = stationExternalId;
        this.timestamp = timestamp;
        this.primaryValue = primaryValue;
        this.secondaryValue = secondaryValue;
    }

    public static ReadingDto fromEntity(Reading r) {
        return new ReadingDto(
                r.getStation().getExternalId(),
                r.getTimestamp(),
                r.getPrimaryValue(),
                r.getSecondaryValue()
        );
    }

    public String getStationExternalId() { return stationExternalId; }
    public void setStationExternalId(String stationExternalId) { this.stationExternalId = stationExternalId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Double getPrimaryValue() { return primaryValue; }
    public void setPrimaryValue(Double primaryValue) { this.primaryValue = primaryValue; }

    public Double getSecondaryValue() { return secondaryValue; }
    public void setSecondaryValue(Double secondaryValue) { this.secondaryValue = secondaryValue; }
}
