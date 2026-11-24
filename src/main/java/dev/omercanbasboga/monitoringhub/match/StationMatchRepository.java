package dev.omercanbasboga.monitoringhub.match;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public interface StationMatchRepository extends JpaRepository<StationMatch, UUID> {

    /** Only the demo feed uses this; created_at is otherwise never written after insert. */
    @Modifying
    @Transactional
    @Query("update StationMatch m set m.createdAt = :createdAt")
    int backdateAll(@Param("createdAt") Instant createdAt);
}
