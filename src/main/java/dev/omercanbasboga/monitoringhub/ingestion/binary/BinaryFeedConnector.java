package dev.omercanbasboga.monitoringhub.ingestion.binary;

import dev.omercanbasboga.monitoringhub.model.Reading;
import dev.omercanbasboga.monitoringhub.model.SourceType;
import dev.omercanbasboga.monitoringhub.model.Station;
import dev.omercanbasboga.monitoringhub.service.StationDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scans the last hour of GCF file drops (see {@link GcfFtpClient}), parses
 * every channel via {@link GcfBinaryParser}, and writes each sample as a
 * {@link Reading} keyed by {@code stationId:channelId}. Runs once on startup
 * and then on whatever schedule {@link dev.omercanbasboga.monitoringhub.config.DynamicSchedulingConfig}
 * assigns to {@code FTP_BINARY}.
 */
@Component
public class BinaryFeedConnector {

    private static final Logger logger = LoggerFactory.getLogger(BinaryFeedConnector.class);

    @Value("${connectors.ftp-binary.host:}")
    private String host;
    @Value("${connectors.ftp-binary.port:21}")
    private int port;
    @Value("${connectors.ftp-binary.username:}")
    private String username;
    @Value("${connectors.ftp-binary.password:}")
    private String password;
    @Value("${connectors.ftp-binary.passive:true}")
    private boolean passive;

    private final StationDataService stationDataService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BinaryFeedConnector(StationDataService stationDataService) {
        this.stationDataService = stationDataService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        scheduledImport();
    }

    @Async
    public void scheduledImport() {
        if (host.isBlank()) return; // connector not configured, skip silently
        if (!running.compareAndSet(false, true)) {
            logger.warn("Previous binary feed scan still running, skipping this tick.");
            return;
        }
        try {
            LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
            LocalDateTime scanStart = nowUtc.minusHours(1);
            ImportSummary summary = importRange(scanStart.toLocalDate(), scanStart.getHour(), nowUtc.toLocalDate(), nowUtc.getHour());
            if (summary.saved() > 0) logger.info("Binary feed scan complete: {}", summary);
        } catch (Exception e) {
            logger.error("Binary feed scan failed: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    public ImportSummary importRange(LocalDate fromDate, int fromHour, LocalDate toDate, int toHour) throws Exception {
        int totalSaved = 0;
        int totalHours = 0;

        try (GcfFtpClient client = new GcfFtpClient(host, port, username, password, passive)) {
            client.connect();

            LocalDate currentDate = fromDate;
            while (!currentDate.isAfter(toDate)) {
                int startHour = currentDate.equals(fromDate) ? fromHour : 0;
                int endHour = currentDate.equals(toDate) ? toHour : 23;

                for (int hour = startHour; hour <= endHour; hour++) {
                    try {
                        List<GcfBinaryParser.GcfRecord> records = client.parseHour(currentDate, hour);
                        for (GcfBinaryParser.GcfRecord record : records) {
                            totalSaved += persist(record);
                        }
                        totalHours++;
                    } catch (Exception e) {
                        logger.debug("Hour {} {}:00 skipped: {}", currentDate, hour, e.getMessage());
                    }
                }
                currentDate = currentDate.plusDays(1);
            }
        }

        return new ImportSummary(fromDate, toDate, totalSaved, totalHours);
    }

    private int persist(GcfBinaryParser.GcfRecord record) {
        String externalId = record.stationId + ":" + record.channelId;
        Station station = stationDataService.findOrCreateStation(SourceType.FTP_BINARY, externalId, record.getChannelDescription());

        List<Reading> candidates = new ArrayList<>();
        for (int i = 0; i < record.values.length; i++) {
            var timestamp = record.timeAt(i).toInstant(ZoneOffset.UTC);
            candidates.add(new Reading(station, timestamp, record.values[i], null));
        }
        return stationDataService.saveNewReadings(station, candidates);
    }

    public record ImportSummary(LocalDate fromDate, LocalDate toDate, int saved, int hoursScanned) {
        @Override
        public String toString() {
            return String.format("saved=%d hoursScanned=%d range=%s..%s", saved, hoursScanned, fromDate, toDate);
        }
    }
}
