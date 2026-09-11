package dev.omercanbasboga.monitoringhub.ingestion.csv;

import dev.omercanbasboga.monitoringhub.model.Reading;
import dev.omercanbasboga.monitoringhub.model.SourceType;
import dev.omercanbasboga.monitoringhub.model.Station;
import dev.omercanbasboga.monitoringhub.service.StationDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ingests a single wide CSV feed published over HTTP: one timestamp column
 * followed by one value column per station. Common shape for public
 * environmental-agency "realtime" exports.
 */
@Component
public class CsvFeedConnector {

    private static final Logger logger = LoggerFactory.getLogger(CsvFeedConnector.class);
    private static final int BATCH_SIZE = 2000;

    @Value("${connectors.csv.feed-url:}")
    private String feedUrl;

    private final RestTemplate restTemplate;
    private final StationDataService stationDataService;

    public CsvFeedConnector(RestTemplate restTemplate, StationDataService stationDataService) {
        this.restTemplate = restTemplate;
        this.stationDataService = stationDataService;
    }

    @Transactional
    public void importFromCsv() {
        if (feedUrl.isBlank()) return;

        String csvData;
        try {
            csvData = restTemplate.getForObject(feedUrl, String.class);
            if (csvData == null || csvData.isEmpty()) {
                logger.warn("CSV feed returned empty content.");
                return;
            }
        } catch (Exception e) {
            logger.error("CSV feed fetch failed: {}", e.getMessage());
            return;
        }

        try (BufferedReader br = new BufferedReader(new StringReader(csvData))) {
            String headerLine = br.readLine();
            if (headerLine == null) return;

            String[] headers = headerLine.split(";", -1);
            List<String> stationNames = Arrays.asList(headers).subList(1, headers.length);

            Map<String, Station> stationMap = new HashMap<>();
            for (String name : stationNames) {
                if (name == null || name.isBlank()) continue;
                stationMap.put(name, stationDataService.findOrCreateStation(SourceType.CSV_FEED, name, name));
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);

            Map<Station, List<Reading>> batchByStation = new HashMap<>();
            int savedTotal = 0;
            String line;

            while ((line = br.readLine()) != null) {
                String[] values = line.split(";", -1);
                if (values.length < headers.length) continue;

                Instant timestamp;
                try {
                    timestamp = LocalDateTime.parse(values[0], formatter).toInstant(ZoneOffset.UTC);
                } catch (Exception e) { continue; }

                if (timestamp.isBefore(oneDayAgo)) continue;

                for (int i = 1; i < headers.length; i++) {
                    String name = stationNames.get(i - 1);
                    Station station = stationMap.get(name);
                    String raw = values[i].trim();
                    if (station == null || raw.isEmpty() || raw.equalsIgnoreCase("NA")) continue;

                    try {
                        double value = Double.parseDouble(raw.replace(",", "."));
                        batchByStation.computeIfAbsent(station, s -> new ArrayList<>())
                                .add(new Reading(station, timestamp, value, null));
                    } catch (NumberFormatException ignored) {
                        logger.debug("Unparseable CSV value for {}: {}", name, raw);
                    }
                }

                for (Map.Entry<Station, List<Reading>> entry : batchByStation.entrySet()) {
                    if (entry.getValue().size() >= BATCH_SIZE) {
                        savedTotal += stationDataService.saveNewReadings(entry.getKey(), entry.getValue());
                        entry.getValue().clear();
                    }
                }
            }

            for (Map.Entry<Station, List<Reading>> entry : batchByStation.entrySet()) {
                if (!entry.getValue().isEmpty()) savedTotal += stationDataService.saveNewReadings(entry.getKey(), entry.getValue());
            }

            if (savedTotal > 0) logger.info("CSV feed import: {} new readings saved.", savedTotal);
        } catch (Exception e) {
            logger.error("CSV feed processing failed: {}", e.getMessage());
        }
    }
}
