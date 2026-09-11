package dev.omercanbasboga.monitoringhub.repository;

import dev.omercanbasboga.monitoringhub.model.Reading;
import dev.omercanbasboga.monitoringhub.model.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ReadingRepository extends JpaRepository<Reading, UUID> {

    List<Reading> findByStationAndTimestampBetweenOrderByTimestampAsc(Station station, Instant start, Instant end);

    @Query("select r.timestamp from Reading r where r.station = :station and r.timestamp in :timestamps")
    Set<Instant> findExistingTimestamps(@Param("station") Station station, @Param("timestamps") Collection<Instant> timestamps);

    @Query("select max(r.timestamp) from Reading r where r.station = :station")
    Instant findLatestTimestamp(@Param("station") Station station);
}
