package com.albaraka.backend.services;

import com.albaraka.backend.services.AgentWire;
import com.albaraka.backend.services.AgentWire.AgentReply;
import com.albaraka.backend.services.AgentWire.StreamDone;
import com.albaraka.backend.services.AgentWire.StreamListener;
import com.albaraka.backend.services.AgentWire.Turn;
import com.albaraka.backend.services.RagLabViews.AnswerView;
import com.albaraka.backend.services.RagLabViews.ClaimView;
import com.albaraka.backend.services.RagLabViews.SourceView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The external RAG service, dressed as a {@link AgentAdapter}.
 *
 * <p>What the edge gets is a normal reply: the same {@link AgentReply} the in-house
 * agent produces, so quotas, metering, persistence and the SSE framing are
 * untouched by the switch. Two capability gaps are handled honestly rather than
 * hidden:
 *
 * <ul>
 *   <li><b>No token stream.</b> RAGLab answers in one piece, so a streamed turn
 *       emits a single delta and then the done frame. The browser gets the answer
 *       at once instead of progressively.</li>
 *   <li><b>No pipeline trace.</b> {@code explain} probes retrieval instead, because
 *       "which passages were retrieved" is the only thing there is to show.</li>
 * </ul>
 *
 * <p>The claims and their verbatim quotes are carried into the trace JSON, which
 * is what the monitoring page replays for a RAGLab turn.
 */
@Service
public class RagLabServingAgent implements AgentAdapter {

    /** Marks a turn served by the external engine (also stored in {@code messages.domaine}). */
    public static final String PROVIDER = "RAGLAB";

    private static final String DEFAULT_REFUSAL =
            "Je ne dispose pas d'information fiable dans les documents pour répondre à cette question.";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RagLabClient client;

    public RagLabServingAgent(RagLabClient client) {
        this.client = client;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String label() {
        return "RAGLab";
    }

    /** Where the service lives, or null when no address has been configured yet. */
    @Override
    public String url() {
        return client.endpointUrl();
    }

    /**
     * {@code GET /health}, read through the client. The failure is data on
     * purpose: an unconfigured or unreachable service is a state the console
     * shows, not a reason for the page to throw.
     */
    @Override
    public AgentAdapter.Health health() {
        RagLabClient.Health probe = client.health();
        return new AgentAdapter.Health(probe.reachable(), probe.httpStatus(), probe.latencyMs(),
                probe.error(), probe.errorReason());
    }

    @Override
    public AgentReply invoke(String sessionId, String message, String language, List<Turn> history) {
        // RAGLab is stateless and reads no history: follow-up questions are answered
        // as standalone ones, exactly as the in-house agent does today.
        RagLabClient.Result result = client.answer(message, null, Boolean.FALSE,
                queryLang(language), null, null);
        return toReply(result.payload(), message);
    }

    @Override
    public void invokeStream(String sessionId, String message, String language, List<Turn> history,
                             StreamListener listener) {
        try {
            AgentReply reply = invoke(sessionId, message, language, history);
            // One delta, and only for a grounded answer. This engine cannot stream, so
            // there is no progress to preserve, and the wording of a refusal is the
            // edge's to replace (see RefusalHandling) — typing out the service's own
            // sentence about "the supplied documents", list and all, would put it on a
            // customer's screen for the instant before the terminal frame discards it.
            if (reply.grounded()) {
                listener.onDelta(reply.text());
            }
            listener.onDone(new StreamDone(reply.grounded(), reply.text(),
                    structured(reply), reply.sources(), reply.traceJson()));
        } catch (RuntimeException e) {
            listener.onError(e);
        }
    }

    @Override
    public Map<String, Object> explain(String message, String language) {
        RagLabClient.Result result = client.search(message, null, null, null, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engine", PROVIDER);
        out.put("latencyMs", result.latencyMs());
        out.put("retrieval", RagLabViews.search(result.payload()));
        out.put("raw", result.payload());
        // The monitoring page knows this engine has no pipeline; an empty step list
        // keeps every existing reader of this payload working.
        out.put("trace", Map.of("steps", List.of()));
        return out;
    }

    // ------------------------------------------------------------------ mapping

    /**
     * Builds the edge-facing reply from a RAGLab answer payload.
     *
     * <p>{@code grounded} means "the service says it answered or greeted": the only
     * prose a customer reads is the service's own answer, whose claims carry
     * verbatim quotes in the trace. Nothing here re-verifies that text — the
     * numeric gate and the PII scrub the in-house pipeline applies do not exist on
     * this path, which the switch screen states before an admin chooses it.
     */
    public static AgentReply toReply(JsonNode answer, String question) {
        AnswerView view = RagLabViews.answer(answer);
        String status = view == null || view.status() == null
                ? "unknown"
                : view.status().trim().toLowerCase(Locale.ROOT);
        String text = view == null ? null : view.answerText();
        if (text == null || text.isBlank()) {
            text = renderClaims(view);
        }
        if (text == null || text.isBlank()) {
            text = DEFAULT_REFUSAL;
        }
        boolean grounded = "answered".equals(status) || "greeting".equals(status);
        boolean ragUsed = "answered".equals(status);

        List<String> sources = sourceIds(view);
        List<String> titles = sourceTitles(view);
        int estimatedTokens = (text.length() / 4) + (question == null ? 0 : question.length() / 4);

        return new AgentReply(text, grounded, intention(view, status), null, PROVIDER,
                sources, ragUsed, estimatedTokens, traceJson(answer, view, question), titles);
    }

    /** The structured block the stream path forwards to the browser. */
    public static Map<String, Object> structured(AgentReply reply) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("domain", PROVIDER);
        out.put("intent", reply.intention());
        if (reply.sourceTitles() != null && !reply.sourceTitles().isEmpty()) {
            out.put("citation_titles", reply.sourceTitles());
        }
        return out;
    }

    // ---------------------------------------------------------------- internals

    /** The service only accepts fr/ar/en; anything else is left to its own default. */
    static String queryLang(String language) {
        if (language == null) {
            return null;
        }
        String value = language.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "fr", "ar", "en" -> value;
            default -> null;
        };
    }

    /** {@code RAGLAB_ANSWERED} · {@code RAGLAB_REFUSED_<REASON>} · {@code RAGLAB_GREETING}. */
    static String intention(AnswerView view, String status) {
        String base = "RAGLAB_" + status.toUpperCase(Locale.ROOT);
        if ("refused".equals(status) && view != null && view.reason() != null
                && !view.reason().isBlank()) {
            return base + "_" + view.reason().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        }
        return base;
    }

    /** Last resort when the service answers with neither prose nor claims. */
    private static String renderClaims(AnswerView view) {
        if (view == null || view.claims() == null || view.claims().isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (ClaimView claim : view.claims()) {
            if (claim.text() != null && !claim.text().isBlank()) {
                parts.add(claim.text().trim());
            }
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    /**
     * The documents a RAGLab turn actually read, in the form the admin console and
     * the document-usage governance query need: the document name (the ingestion
     * key in {@code documents.nom_fichier}), never the service's internal alias.
     *
     * <p>{@code source_id} is "S1", "S2" … — a label meaningful only inside one
     * answer, and exactly what a reviewer must not be shown in place of a document.
     * It is kept as a fallback for a payload with no document name at all, so a
     * turn is never recorded as "no source" when the service did read something.
     */
    private static List<String> sourceIds(AnswerView view) {
        List<String> ids = new ArrayList<>();
        if (view != null && view.sources() != null) {
            for (SourceView source : view.sources()) {
                String document = source.document();
                String reference = document == null || document.isBlank() ? source.sourceId() : document;
                if (reference != null && !reference.isBlank() && !ids.contains(reference.trim())) {
                    ids.add(reference.trim());
                }
            }
        }
        return ids;
    }

    /** Human titles for the UI: "document — section", falling back to the document. */
    private static List<String> sourceTitles(AnswerView view) {
        List<String> titles = new ArrayList<>();
        if (view != null && view.sources() != null) {
            for (SourceView source : view.sources()) {
                String title = source.document() == null || source.document().isBlank()
                        ? source.sourceId()
                        : (source.heading() == null || source.heading().isBlank()
                                ? source.document()
                                : source.document() + " — " + source.heading());
                if (title != null && !title.isBlank()) {
                    titles.add(title);
                }
            }
        }
        return titles;
    }

    /**
     * The replayable trace of a RAGLab turn: what the service decided, which
     * sources it returned, which of them no claim used, and every claim with its
     * verbatim quotes — the part an operator actually audits.
     */
    static String traceJson(JsonNode answer, AnswerView view, String question) {
        if (view == null) {
            return null;
        }
        ObjectNode trace = JSON.createObjectNode();
        trace.put("engine", PROVIDER);
        trace.put("status", view.status());
        if (view.reason() != null) {
            trace.put("reason", view.reason());
        }
        if (view.seconds() != null) {
            trace.put("seconds", view.seconds());
        }
        if (view.cached() != null) {
            trace.put("cached", view.cached());
        }
        if (view.inferencePerformed() != null) {
            trace.put("inference_performed", view.inferencePerformed());
        }
        trace.put("question", question);

        ArrayNode claims = trace.putArray("claims");
        if (view.claims() != null) {
            for (ClaimView claim : view.claims()) {
                ObjectNode node = claims.addObject();
                node.put("text", claim.text());
                node.put("cited", claim.cited());
                ArrayNode evidence = node.putArray("evidence");
                if (claim.evidence() != null) {
                    for (RagLabViews.EvidenceView item : claim.evidence()) {
                        ObjectNode quote = evidence.addObject();
                        quote.put("source_id", item.sourceId());
                        quote.put("quote", item.quote());
                    }
                }
            }
        }

        ArrayNode sources = trace.putArray("sources");
        if (view.sources() != null) {
            for (SourceView source : view.sources()) {
                ObjectNode node = sources.addObject();
                node.put("source_id", source.sourceId());
                node.put("document", source.document());
                node.put("chunk_id", source.chunkId());
                node.put("heading", source.heading());
            }
        }

        ArrayNode uncited = trace.putArray("uncited_source_ids");
        if (view.uncitedSourceIds() != null) {
            view.uncitedSourceIds().forEach(uncited::add);
        }
        return trace.toString();
    }
}
