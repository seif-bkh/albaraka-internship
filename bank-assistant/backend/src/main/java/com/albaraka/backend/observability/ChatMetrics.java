package com.albaraka.backend.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Business metrics for the public chat (Phase 6.1), exposed through Actuator's
 * Prometheus endpoint alongside the JVM/HTTP defaults.
 *
 * <ul>
 *   <li>{@code chat_turns_total{mode,outcome}} — turns by transport (sync/stream)
 *       and outcome (grounded / refused / error / quota).</li>
 *   <li>{@code chat_turn_duration} — end-to-end latency as seen by the edge.</li>
 *   <li>{@code chat_tokens_estimated_total{mode}} — token spend proxy per turn,
 *       the same estimate the quota uses, so budgets and dashboards agree.</li>
 * </ul>
 */
@Component
public class ChatMetrics {

    private final MeterRegistry registry;

    public ChatMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void turn(String mode, String outcome, Duration latency, int estimatedTokens) {
        Counter.builder("chat_turns_total")
                .description("Public chat turns by transport and outcome")
                .tag("mode", mode).tag("outcome", outcome)
                .register(registry).increment();
        Timer.builder("chat_turn_duration")
                .description("End-to-end latency of one public chat turn")
                .tag("mode", mode).tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(registry).record(latency);
        if (estimatedTokens > 0) {
            Counter.builder("chat_tokens_estimated_total")
                    .description("Estimated tokens consumed by public chat turns")
                    .tag("mode", mode)
                    .register(registry).increment(estimatedTokens);
        }
    }

    public void quotaHit(String mode) {
        Counter.builder("chat_turns_total")
                .description("Public chat turns by transport and outcome")
                .tag("mode", mode).tag("outcome", "quota")
                .register(registry).increment();
    }
}
