package dev.omercanbasboga.monitoringhub.repository;

import dev.omercanbasboga.monitoringhub.model.SourceType;
import dev.omercanbasboga.monitoringhub.model.Station;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StationRepository extends JpaRepository<Station, UUID> {
    Optional<Station> findBySourceTypeAndExternalId(SourceType sourceType, String externalId);
    List<Station> findBySourceType(SourceType sourceType);
    List<Station> findByActiveTrue();
}
