package dev.omercanbasboga.monitoringhub.match;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceUsageRepository extends JpaRepository<SourceUsage, UUID> {

    List<SourceUsage> findByMatchIdOrderByStartedAtDesc(UUID matchId);

    Optional<SourceUsage> findFirstByMatchIdAndEndedAtIsNullOrderByStartedAtDesc(UUID matchId);
}
