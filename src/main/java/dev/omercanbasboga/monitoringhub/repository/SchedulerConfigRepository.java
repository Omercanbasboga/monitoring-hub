package dev.omercanbasboga.monitoringhub.repository;

import dev.omercanbasboga.monitoringhub.model.SchedulerConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulerConfigRepository extends JpaRepository<SchedulerConfig, String> {
}
