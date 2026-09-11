package dev.omercanbasboga.monitoringhub.config;

import org.springframework.lang.Nullable;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

import java.time.Instant;
import java.util.function.LongSupplier;

/**
 * Behaves like a fixedRate trigger but re-reads its interval on every firing
 * instead of baking it in at startup, so a runtime interval change (via
 * {@link dev.omercanbasboga.monitoringhub.service.SchedulerConfigService}) takes
 * effect on the next tick without a restart.
 */
public class DynamicIntervalTrigger implements Trigger {

    private final LongSupplier intervalMsSupplier;

    public DynamicIntervalTrigger(LongSupplier intervalMsSupplier) {
        this.intervalMsSupplier = intervalMsSupplier;
    }

    @Override
    @Nullable
    public Instant nextExecution(TriggerContext triggerContext) {
        Instant lastCompletion = triggerContext.lastCompletion();
        Instant base = (lastCompletion != null) ? lastCompletion : triggerContext.getClock().instant();
        return base.plusMillis(intervalMsSupplier.getAsLong());
    }
}
