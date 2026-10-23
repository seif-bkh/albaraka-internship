package com.albaraka.backend.services;

import com.albaraka.backend.services.AgentWire;
import com.albaraka.backend.services.AgentWire.AgentReply;
import com.albaraka.backend.services.AgentWire.StreamListener;
import com.albaraka.backend.services.AgentWire.Turn;

import java.util.List;
import java.util.Map;

/**
 * One engine that can answer a customer turn, as the gateway sees it.
 *
 * <p>Every agent — the in-house pipeline and the external RAG service alike —
 * answers the same questions, so the edge (quotas, metering, persistence, SSE
 * framing, monitoring) stays engine-agnostic and adding an engine is a
 * deployment decision rather than a rewrite.
 *
 * <p>Engines differ in <em>capability</em>, not in contract. The in-house agent
 * streams token by token; RAGLab cannot and answers in one piece, so callers
 * must not assume more than one {@code onDelta}. The console asks each adapter
 * what it supports rather than assuming, which is why {@link #health()} is part
 * of the contract: an agent that cannot be reached must be able to say so as
 * data rather than as an exception.
 *
 * <p>The surface is deliberately the part both engines can honour today.
 * Configuration and the document feed arrive with their own steps and will
 * extend this interface — they are not faked in the meantime, because a method
 * that silently does nothing is worse than a method that does not exist.
 */
public interface AgentAdapter {

    /**
     * Outcome of a liveness probe: the failure is data, not an exception.
     *
     * <p>The console shows these side by side, so a reachable-but-unconfigured
     * agent must not read as a down one.
     */
    record Health(boolean reachable, Integer httpStatus, long latencyMs, String error,
                  String errorReason) {
    }

    /** Wire name of this engine: {@code LEGACY} or {@code RAGLAB}. */
    String provider();

    /** What the console calls it: {@code Agent interne}, {@code RAGLab}. */
    String label();

    /** Where it lives, or {@code null} when no address is configured yet. */
    String url();

    /** Answer one turn and return the reply the edge persists and serves. */
    AgentReply invoke(String sessionId, String message, String language, List<Turn> history);

    /** Answer one turn, relaying whatever the engine can relay. */
    void invokeStream(String sessionId, String message, String language, List<Turn> history,
                      StreamListener listener);

    /**
     * Operator diagnostics for one question: the pipeline trace for the in-house
     * agent, the retrieved chunks for RAGLab. Free-form because the two engines
     * have genuinely different internals — the monitoring page renders each shape.
     */
    Map<String, Object> explain(String message, String language);

    /** Is this agent answering right now? Never throws; the failure is the payload. */
    Health health();
}
