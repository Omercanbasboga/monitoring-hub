package dev.omercanbasboga.monitoringhub.ingestion.ftp;

import dev.omercanbasboga.monitoringhub.model.Reading;
import dev.omercanbasboga.monitoringhub.model.SourceType;
import dev.omercanbasboga.monitoringhub.model.Station;
import dev.omercanbasboga.monitoringhub.service.StationDataService;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Polls an FTP server's root directory for newly-dropped delimited data files
 * (one file per station per sync run) and imports each into the shared
 * reading table. Originally written for a national mapping agency's
 * comma-separated {@code *_Sec30.dat} tide-gauge exports; the file layout is
 * configurable via {@code connectors.ftp-delimited.*} so any "one CSV-ish
 * file per station, dropped on an FTP root" feed fits the same connector.
 */
@Component
public class DelimitedFtpConnector {

    private static final Logger logger = LoggerFactory.getLogger(DelimitedFtpConnector.class);

    @Value("${connectors.ftp-delimited.host:}")
    private String host;
    @Value("${connectors.ftp-delimited.username:}")
    private String username;
    @Value("${connectors.ftp-delimited.password:}")
    private String password;
    @Value("${connectors.ftp-delimited.file-suffix:.dat}")
    private String fileSuffix;

    private final StationDataService stationDataService;
    private boolean running = false;

    public DelimitedFtpConnector(StationDataService stationDataService) {
        this.stationDataService = stationDataService;
    }

    public synchronized void importFromFtp() {
        if (host.isBlank()) return; // connector not configured, skip silently
        if (running) {
            logger.warn("Previous FTP delimited import still running, skipping this tick.");
            return;
        }
        running = true;
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.setConnectTimeout(60_000);
            ftpClient.connect(host);
            ftpClient.setSoTimeout(30_000);
            ftpClient.setDataTimeout(java.time.Duration.ofSeconds(30));
            ftpClient.login(username, password);
            ftpClient.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE);
            ftpClient.enterLocalPassiveMode();

            FTPFile[] files = ftpClient.listFiles("/");
            if (files != null) {
                for (FTPFile f : files) {
                    if (f.isFile() && f.getName().endsWith(fileSuffix)) {
                        processFile(ftpClient, f.getName());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("FTP delimited import failed: {}", e.getMessage());
        } finally {
            running = false;
            try { if (ftpClient.isConnected()) ftpClient.disconnect(); } catch (IOException ignored) {}
        }
    }

    private void processFile(FTPClient ftpClient, String fileName) {
        try {
            File tempFile = File.createTempFile("ftp_delimited_", ".tmp");
            try (OutputStream os = new FileOutputStream(tempFile)) {
                if (ftpClient.retrieveFile(fileName, os)) {
                    parseAndSave(tempFile);
                }
            } finally {
                Files.deleteIfExists(tempFile.toPath());
            }
        } catch (Exception e) {
            logger.error("FTP file {} failed: {}", fileName, e.getMessage());
        }
    }

    /**
     * File shape: header row's 2nd comma-separated field is the station name;
     * first 3 data rows are further metadata and skipped; remaining rows are
     * {@code "timestamp","recordId",value}.
     */
    private void parseAndSave(File file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String stationName = headerLine.split(",")[1].replace("\"", "").trim();
            if (stationName.isEmpty()) return;

            Station station = stationDataService.findOrCreateStation(SourceType.FTP_DELIMITED, stationName, stationName);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            List<Reading> batch = new ArrayList<>();
            String line;
            int rowNum = 0, saved = 0;

            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (rowNum < 4) continue; // skip metadata rows

                try {
                    String[] p = line.split(",");
                    var timestamp = LocalDateTime.parse(p[0].replace("\"", "").trim(), fmt).toInstant(ZoneOffset.UTC);
                    double value = Double.parseDouble(p[2].trim());
                    batch.add(new Reading(station, timestamp, value, null));

                    if (batch.size() >= 500) {
                        saved += stationDataService.saveNewReadings(station, batch);
                        batch.clear();
                    }
                } catch (Exception e) {
                    logger.debug("Unparseable row (skipped): {}", line);
                }
            }
            if (!batch.isEmpty()) saved += stationDataService.saveNewReadings(station, batch);
            if (saved > 0) logger.info("FTP delimited import: {} new readings for {}.", saved, stationName);
        } catch (Exception e) {
            logger.error("Failed reading delimited file {}: {}", file.getName(), e.getMessage());
        }
    }
}
