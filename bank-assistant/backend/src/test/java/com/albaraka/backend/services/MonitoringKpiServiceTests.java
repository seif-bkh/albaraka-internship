package com.albaraka.backend.services;

import com.albaraka.backend.services.MonitoringKpiService.EngineKpis;
import com.albaraka.backend.services.MonitoringKpiService.TurnFact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The arithmetic behind the monitoring KPI strip: which engine answered, how
 * often it refused, and — the point of the whole exercise — a comparison that
 * cannot be fudged by mixing the two engines together.
 */
class MonitoringKpiServiceTests {

    private static EngineKpis kpisFor(List<EngineKpis> all, String engine) {
        return all.stream().filter(k -> k.engine().equals(engine)).findFirst().orElseThrow();
    }

    @Test
    void attributionComesFromTheIntentionSuffixTheMapperWrites() {
        assertEquals("RAGLAB", MonitoringKpiService.engineOf("RAGLAB_ANSWERED"));
        assertEquals("RAGLAB", MonitoringKpiService.engineOf("RAGLAB_REFUSED_NO_CONTEXT"));
        assertEquals("LEGACY", MonitoringKpiService.engineOf("RATE"));
        assertEquals("LEGACY", MonitoringKpiService.engineOf("KNOWLEDGE_GAP"),
                "the in-house pipeline states its own intentions");
        assertEquals("LEGACY", MonitoringKpiService.engineOf(null));
    }

    @Test
    void refusalReasonsAreReadFromTheMachineCodeOnly() {
        assertEquals("insufficient_evidence",
                MonitoringKpiService.refusalReason("RAGLAB_REFUSED_INSUFFICIENT_EVIDENCE"));
        assertEquals("no_context", MonitoringKpiService.refusalReason("RAGLAB_REFUSED_NO_CONTEXT"));
        assertNull(MonitoringKpiService.refusalReason("RAGLAB_ANSWERED"));
        assertNull(MonitoringKpiService.refusalReason("RAGLAB_REFUSED_"));
        assertNull(MonitoringKpiService.refusalReason("KNOWLEDGE_GAP"));
        assertNull(MonitoringKpiService.refusalReason(null));
    }

    @Test
    void eachEngineGetsItsOwnNumbers() {
        List<EngineKpis> all = MonitoringKpiService.compute(List.of(
                new TurnFact("RAGLAB_ANSWERED", true, true),
                new TurnFact("RAGLAB_ANSWERED", true, false),
                new TurnFact("RAGLAB_REFUSED_INSUFFICIENT_EVIDENCE", false, true),
                new TurnFact("RAGLAB_REFUSED_INSUFFICIENT_EVIDENCE", false, false),
                new TurnFact("RAGLAB_REFUSED_NO_CONTEXT", false, false),
                new TurnFact("RATE", true, true),
                new TurnFact("KNOWLEDGE_GAP", false, false)));

        EngineKpis raglab = kpisFor(all, "RAGLAB");
        assertEquals(5, raglab.turns());
        assertEquals(2, raglab.grounded());
        assertEquals(3, raglab.refused());
        assertEquals(0.4, raglab.groundingRate(), 0.001);
        assertEquals(2, raglab.withSources());
        assertEquals(0.4, raglab.sourcesRate(), 0.001);
        assertEquals(List.of("insufficient_evidence", "no_context"),
                raglab.refusalReasons().stream().map(r -> r.reason()).toList());
        assertEquals(2, raglab.refusalReasons().get(0).count());

        EngineKpis legacy = kpisFor(all, "LEGACY");
        assertEquals(2, legacy.turns());
        assertEquals(1, legacy.grounded());
        assertEquals(0.5, legacy.groundingRate(), 0.001);
        assertEquals(List.of("sans_code"), legacy.refusalReasons().stream().map(r -> r.reason()).toList(),
                "the in-house pipeline's refusals carry no machine code");
    }

    @Test
    void anEngineWithNoTrafficReportsNoRateRatherThanZero() {
        // 0% would read as "it always refuses", which is not what "nobody asked it" means.
        List<EngineKpis> all = MonitoringKpiService.compute(List.of(new TurnFact("RATE", true, true)));
        EngineKpis raglab = kpisFor(all, "RAGLAB");

        assertEquals(0, raglab.turns());
        assertNull(raglab.groundingRate());
        assertNull(raglab.sourcesRate());
        assertEquals(List.of(), raglab.refusalReasons());
        assertEquals("RAGLab", raglab.label());
    }

    @Test
    void anEmptyWindowIsHandled() {
        List<EngineKpis> all = MonitoringKpiService.compute(List.of());
        assertEquals(2, all.size(), "both engines are always listed, so the strip never blinks");
        assertNull(kpisFor(all, "LEGACY").groundingRate());
        assertNull(kpisFor(all, "RAGLAB").groundingRate());

        assertEquals(2, MonitoringKpiService.compute(null).size());
    }
}
