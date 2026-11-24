package dev.omercanbasboga.monitoringhub.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Polling interval for one source, keyed by the {@link SourceType} name. */
@Entity
@Table(name = "scheduler_config")
public class SchedulerConfig {

    @Id
    @Column(name = "source_name", length = 32)
    private String sourceName;

    @Column(name = "interval_ms", nullable = false)
    private long intervalMs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public long getIntervalMs() { return intervalMs; }
    public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
