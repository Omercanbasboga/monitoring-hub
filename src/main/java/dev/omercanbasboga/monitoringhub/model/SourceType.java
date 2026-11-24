package dev.omercanbasboga.monitoringhub.model;

/** How a station's data reaches us. One connector per value. */
public enum SourceType {
    REST_API,
    CSV_FEED,
    FTP_DELIMITED,
    FTP_BINARY
}
