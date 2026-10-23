package com.albaraka.backend.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a window of stored exchanges into the numbers the monitoring page shows,
 * per engine.
 *
 * <p>Which engine answered a turn is read from its intention: RAGLab turns are
 * named {@code RAGLAB_<STATUS>} (and {@code RAGLAB_REFUSED_<REASON>}) by
 * {@link RagLabServingAgent}. RAGLab is now the only engine, but the exchanges
 * already stored were written by the old in-house pipeline, and a report that
 * relabelled them as RAGLab turns would be a lie about history. They keep their
 * own bucket, clearly marked as historical, and no code serves with that engine
 * any more.
 *
 * <p>Pure and static on purpose: the arithmetic is unit-tested without a database,
 * and the caller decides which window of turns to hand in.
 */
public final class MonitoringKpiService {

    /** Marks the turns served by the external engine. */
    public static final String RAGLAB_PREFIX = "RAGLAB_";

    /** The only engine that serves today. */
    public static final String RAGLAB = RagLabServingAgent.PROVIDER;

    /** Label of the turns stored before RAGLab became the only engine. */
    public static final String INHOUSE_HISTORY = "LEGACY";

    private MonitoringKpiService() {
    }

    /** The minimum a KPI computation needs from a stored exchange. */
    public record TurnFact(String intention, Boolean grounded, boolean hasSources) {
    }

    public record ReasonCount(String reason, long count) {
    }

    /**
     * One engine's numbers over the window. {@code groundingRate} and
     * {@code sourcesRate} are kept null when they would be meaningless (no turns
     * at all) rather than reported as 0%, which would read as "it always refuses".
     */
    public record EngineKpis(String engine, String label, long turns, long grounded, long refused,
                             Double groundingRate, long withSources, Double sourcesRate,
                             List<ReasonCount> refusalReasons) {
    }

    /** {@code RAGLAB} for turns it answered, {@code LEGACY} for the stored history. */
    public static String engineOf(String intention) {
        return intention != null && intention.startsWith(RAGLAB_PREFIX)
                ? RAGLAB
                : INHOUSE_HISTORY;
    }

    /**
     * The machine reason behind a refusal, when the turn carries one:
     * {@code RAGLAB_REFUSED_INSUFFICIENT_EVIDENCE} → {@code insufficient_evidence}.
     * The in-house pipeline states its refusals in its own words, so most of its
     * turns yield null here and are counted as "sans code".
     */
    public static String refusalReason(String intention) {
        if (intention == null) {
            return null;
        }
        String marker = RAGLAB_PREFIX + "REFUSED_";
        if (!intention.startsWith(marker)) {
            return null;
        }
        String reason = intention.substring(marker.length()).trim().toLowerCase(Locale.ROOT);
        return reason.isEmpty() ? null : reason;
    }

    /** Per-engine KPIs, RAGLab first when it served anything, in a stable order. */
    public static List<EngineKpis> compute(List<TurnFact> facts) {
        // Both buckets exist so the comparison stays readable: RAGLab's turns, and
        // the ones stored before it was the only engine. The historical bucket has
        // no serving code behind it any more; its rates are null, not 0%.
        Map<String, List<TurnFact>> byEngine = new LinkedHashMap<>();
        byEngine.put(INHOUSE_HISTORY, new ArrayList<>());
        byEngine.put(RAGLAB, new ArrayList<>());
        if (facts != null) {
            for (TurnFact fact : facts) {
                byEngine.computeIfAbsent(engineOf(fact.intention()), key -> new ArrayList<>()).add(fact);
            }
        }

        List<EngineKpis> out = new ArrayList<>();
        for (Map.Entry<String, List<TurnFact>> entry : byEngine.entrySet()) {
            out.add(summarize(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    private static EngineKpis summarize(String engine, List<TurnFact> facts) {
        long turns = facts.size();
        long grounded = 0;
        long withSources = 0;
        Map<String, Long> reasons = new LinkedHashMap<>();
        for (TurnFact fact : facts) {
            if (Boolean.TRUE.equals(fact.grounded())) {
                grounded++;
            }
            if (fact.hasSources()) {
                withSources++;
            }
            if (!Boolean.TRUE.equals(fact.grounded())) {
                String reason = refusalReason(fact.intention());
                reasons.merge(reason == null ? "sans_code" : reason, 1L, Long::sum);
            }
        }
        List<ReasonCount> refusalReasons = new ArrayList<>();
        reasons.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> refusalReasons.add(new ReasonCount(e.getKey(), e.getValue())));

        return new EngineKpis(
                engine,
                RAGLAB.equals(engine) ? "RAGLab" : "Agent interne (historique)",
                turns,
                grounded,
                turns - grounded,
                turns == 0 ? null : (double) grounded / turns,
                withSources,
                turns == 0 ? null : (double) withSources / turns,
                refusalReasons);
    }
}
