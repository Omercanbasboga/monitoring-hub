package dev.omercanbasboga.monitoringhub.match;

import dev.omercanbasboga.monitoringhub.cache.CacheKeys;
import dev.omercanbasboga.monitoringhub.cache.CacheService;
import dev.omercanbasboga.monitoringhub.model.Station;
import dev.omercanbasboga.monitoringhub.repository.StationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class StationMatchService {

    private final StationMatchRepository matches;
    private final StationRepository stations;
    private final FailoverService failover;
    private final CacheService cache;

    public StationMatchService(StationMatchRepository matches, StationRepository stations,
                               FailoverService failover, CacheService cache) {
        this.matches = matches;
        this.stations = stations;
        this.failover = failover;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public List<MatchDto> list() {
        return matches.findAll().stream()
                .sorted(Comparator.comparing(StationMatch::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public MatchDto get(UUID id) {
        return toDto(require(id));
    }

    @Transactional
    public MatchDto create(MatchRequest req) {
        StationMatch m = new StationMatch();
        apply(m, req);
        return toDto(matches.save(m));
    }

    @Transactional
    public MatchDto update(UUID id, MatchRequest req) {
        StationMatch m = require(id);
        apply(m, req);
        MatchDto saved = toDto(matches.save(m));
        // evict only after commit; evicting earlier lets a concurrent read put the old pairing back
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.evict(CacheKeys.failoverLast24h(id));
            }
        });
        return saved;
    }

    /**
     * Copies the request onto the match and switches both stations on, so the
     * REST connector starts polling them if it wasn't already.
     */
    private void apply(StationMatch m, MatchRequest req) {
        if (Objects.equals(req.primaryStationId(), req.secondaryStationId())) {
            throw new IllegalArgumentException("Primary and secondary must be different stations.");
        }
        Station primary = station(req.primaryStationId());
        Station secondary = req.secondaryStationId() == null ? null : station(req.secondaryStationId());

        m.setName(req.name().trim());
        m.setLatitude(req.latitude() != null ? req.latitude() : primary.getLatitude());
        m.setLongitude(req.longitude() != null ? req.longitude() : primary.getLongitude());
        m.setPrimaryStationId(primary.getId());
        m.setSecondaryStationId(secondary == null ? null : secondary.getId());

        activate(primary);
        if (secondary != null) activate(secondary);
    }

    private void activate(Station s) {
        if (!s.isActive()) {
            s.setActive(true);
            stations.save(s);
        }
    }

    private MatchDto toDto(StationMatch m) {
        Station primary = stations.findById(m.getPrimaryStationId()).orElse(null);
        Station secondary = m.hasSecondary() ? stations.findById(m.getSecondaryStationId()).orElse(null) : null;
        return MatchDto.of(m, primary, secondary, failover.activeSide(m.getId()).orElse(null));
    }

    private StationMatch require(UUID id) {
        return matches.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown match: " + id));
    }

    private Station station(UUID id) {
        return stations.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown station: " + id));
    }
}
