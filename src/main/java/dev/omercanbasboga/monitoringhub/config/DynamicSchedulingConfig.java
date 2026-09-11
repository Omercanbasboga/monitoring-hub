package dev.omercanbasboga.monitoringhub.config;

import dev.omercanbasboga.monitoringhub.ingestion.binary.BinaryFeedConnector;
import dev.omercanbasboga.monitoringhub.ingestion.csv.CsvFeedConnector;
import dev.omercanbasboga.monitoringhub.ingestion.ftp.DelimitedFtpConnector;
import dev.omercanbasboga.monitoringhub.ingestion.rest.RestPollingConnector;
import dev.omercanbasboga.monitoringhub.model.SourceType;
import dev.omercanbasboga.monitoringhub.model.Station;
import dev.omercanbasboga.monitoringhub.repository.StationRepository;
import dev.omercanbasboga.monitoringhub.service.SchedulerConfigService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.List;

/**
 * Registers each connector's polling job against an interval read live from
 * {@link SchedulerConfigService}, instead of a fixed {@code @Scheduled(fixedRate=...)}
 * baked in at compile time. Source keys match {@link SourceType}.
 */
@Configuration
public class DynamicSchedulingConfig implements SchedulingConfigurer {

    private final RestPollingConnector restPollingConnector;
    private final CsvFeedConnector csvFeedConnector;
    private final DelimitedFtpConnector delimitedFtpConnector;
    private final BinaryFeedConnector binaryFeedConnector;
    private final SchedulerConfigService schedulerConfigService;
    private final StationRepository stationRepository;

    public DynamicSchedulingConfig(RestPollingConnector restPollingConnector,
                                    CsvFeedConnector csvFeedConnector,
                                    DelimitedFtpConnector delimitedFtpConnector,
                                    BinaryFeedConnector binaryFeedConnector,
                                    SchedulerConfigService schedulerConfigService,
                                    StationRepository stationRepository) {
        this.restPollingConnector = restPollingConnector;
        this.csvFeedConnector = csvFeedConnector;
        this.delimitedFtpConnector = delimitedFtpConnector;
        this.binaryFeedConnector = binaryFeedConnector;
        this.schedulerConfigService = schedulerConfigService;
        this.stationRepository = stationRepository;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(this::pollRestSources, trigger(SourceType.REST_API));
        registrar.addTriggerTask(csvFeedConnector::importFromCsv, trigger(SourceType.CSV_FEED));
        registrar.addTriggerTask(delimitedFtpConnector::importFromFtp, trigger(SourceType.FTP_DELIMITED));
        registrar.addTriggerTask(binaryFeedConnector::scheduledImport, trigger(SourceType.FTP_BINARY));
    }

    private void pollRestSources() {
        List<Station> restStations = stationRepository.findBySourceType(SourceType.REST_API);
        restPollingConnector.poll(restStations);
    }

    private DynamicIntervalTrigger trigger(SourceType source) {
        return new DynamicIntervalTrigger(() -> schedulerConfigService.getIntervalMs(source.name()));
    }
}
