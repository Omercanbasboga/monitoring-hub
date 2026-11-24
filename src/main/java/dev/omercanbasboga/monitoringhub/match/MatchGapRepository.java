package dev.omercanbasboga.monitoringhub.match;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface MatchGapRepository extends JpaRepository<MatchGap, UUID> {

    List<MatchGap> findByMatchIdAndSlotTimeGreaterThanEqualOrderBySlotTimeDesc(UUID matchId, Instant since);

    @Query("select g.slotTime from MatchGap g where g.matchId = :matchId and g.side = :side "
            + "and g.slotTime between :from and :to")
    Set<Instant> slotsBetween(@Param("matchId") UUID matchId, @Param("side") Side side,
                              @Param("from") Instant from, @Param("to") Instant to);
}
