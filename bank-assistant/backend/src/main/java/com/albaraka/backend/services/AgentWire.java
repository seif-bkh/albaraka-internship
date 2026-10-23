package com.albaraka.backend.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The wire types shared by the gateway and whatever engine answers a turn.
 *
 * <p>These used to live on the in-house HTTP client. When that client was removed
 * the shapes stayed: the edge (quotas, metering, persistence, SSE framing,
 * monitoring) is written against them, and RAGLab already returns them. Keeping
 * them in a type-only class is what lets "one engine" mean one engine rather than
 * one engine and a vestigial client.
 */
public final class AgentWire {

    private AgentWire() {
    }

    /** MDC key carrying the per-turn correlation id (also sent as X-Turn-Id). */
    public static final String MDC_TURN_ID = "turnId";

    /** One prior turn, passed to the engine for follow-up context. */
    public record Turn(String role, String text) {
    }

    /** The parsed reply the caller persists and serves. */
    public record AgentReply(String text, boolean grounded, String intention,
                             String topic, String domain, List<String> sources,
                             boolean ragUsed, int estimatedTokens, String traceJson,
                             List<String> sourceTitles) {
    }

    /** Terminal state of a streamed turn, as the controller relays it onward. */
    public record StreamDone(boolean grounded, String text,
                             Map<String, Object> structured, List<String> sources,
                             String traceJson) {
        /** Human titles for the UI; falls back to the stable source ids. */
        public List<String> sourceTitles() {
            return titlesOf(structured, sources);
        }
    }

    static List<String> titlesOf(Map<?, ?> structured, List<String> fallback) {
        if (structured != null && structured.get("citation_titles") instanceof List<?> titles && !titles.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (Object t : titles) {
                if (t != null && !t.toString().isBlank()) {
                    out.add(prettyTitle(t.toString()));
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        List<String> out = new ArrayList<>();
        for (String s : fallback) {
            out.add(prettyTitle(s));
        }
        return out;
    }

    /** "Guide_Interne_Ops.docx" -> "Guide Interne Ops". */
    static String prettyTitle(String name) {
        String base = name;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0 && base.length() - dot <= 5) {
            base = base.substring(0, dot);
        }
        return base.replace('_', ' ').replace('-', ' ').trim();
    }

    /** Callback surface for a single streamed turn. */
    public interface StreamListener {
        void onDelta(String text);

        void onDone(StreamDone done);

        void onError(Exception error);
    }
}
