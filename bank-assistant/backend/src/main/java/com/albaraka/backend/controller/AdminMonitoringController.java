package com.albaraka.backend.controller;

import com.albaraka.backend.model.MessageEntity;
import com.albaraka.backend.repository.MessageRepository;
import com.albaraka.backend.services.AgentRouter;
import com.albaraka.backend.services.ChatTraceDocuments;
import com.albaraka.backend.services.MonitoringKpiService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operator view of the chat pipeline: recent exchanges with their outcome and the
 * agent trace (language, classifier candidates, route, retrieved chunks with
 * similarity, gates, LLM prompt), plus an on-demand {@code explain} that runs a
 * question through the agent without touching quotas, history or cache.
 */
@RestController
@RequestMapping("/api/admin/monitoring")
public class AdminMonitoringController {

    private static final String READ = "hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN_DOCUMENTS','ROLE_ADMIN_SUPERVISION','ROLE_VIEWER')";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    /** Upper bound on the exchanges a single read may scan (the console window). */
    private static final int MAX_WINDOW = 200;

    private final MessageRepository messages;
    private final AgentRouter agent;

    public AdminMonitoringController(MessageRepository messages, AgentRouter agent) {
        this.messages = messages;
        this.agent = agent;
    }

    public record TurnSummary(Long id, String dateCreation, String sessionId, String question,
                              String reponse, String intention, String domaine, String langue,
                              Boolean grounded, List<String> sources, boolean hasTrace,
                              String engine, List<ChatTraceDocuments.DocumentRef> documents) {
        static TurnSummary from(MessageEntity m) {
            return new TurnSummary(m.getId(),
                    m.getDateCreation() == null ? null : m.getDateCreation().toString(),
                    m.getConversation() == null ? null : m.getConversation().getSessionId(),
                    m.getQuestion(), m.getReponse(), m.getIntention(), m.getDomaine(), m.getLangue(),
                    m.getGrounded(), m.getSourcesUtilisees(),
                    m.getTraceJson() != null && !m.getTraceJson().isBlank(),
                    MonitoringKpiService.engineOf(m.getIntention()),
                    ChatTraceDocuments.fromTrace(m.getTraceJson(), m.getSourcesUtilisees(),
                            m.getIntention()));
        }
    }

    /**
     * One exchange as the reviewer sees it: the identifiers a customer never gets
     * (row id, user, session), the model and cost, the real timestamps, and — the
     * part only the admin has — the documents the answer was built from.
     *
     * <p>{@code sources} lists documents (ingestion file names / governed-fact keys),
     * not the {@code S1 S2} labels an answer may have carried: those are internal
     * citation aliases and say nothing about provenance. {@code documents} widens that
     * to everything retrieval consulted, including chunks the relevance threshold
     * dropped, which is what makes an answer of poor quality explainable.
     */
    public record RealtimeTurn(
        Long id,
        Long userId,
        Long clientId,
        String sessionId,
        String question,
        String answer,
        List<String> sources,
        List<ChatTraceDocuments.DocumentRef> documents,
        String model,
        Integer tokensIn,
        Integer tokensOut,
        Integer latencyMs,
        String status,
        String askedAt,
        String answeredAt,
        String intention,
        String langue,
        String domaine,
        Boolean grounded,
        boolean hasTrace
    ) {
        static RealtimeTurn from(MessageEntity m) {
            String asked = m.getAskedAt() != null ? m.getAskedAt().toString()
                    : (m.getDateCreation() != null ? m.getDateCreation().toString() : null);
            String answered = m.getAnsweredAt() != null ? m.getAnsweredAt().toString() : null;
            String st = m.getStatus();
            if (st == null || st.isBlank()) {
                st = m.getReponse() == null ? "IN_PROGRESS" : (Boolean.FALSE.equals(m.getGrounded()) ? "REFUSED" : "ANSWERED");
            }
            return new RealtimeTurn(
                m.getId(),
                m.getUserId(),
                m.getConversation() == null ? null : m.getConversation().getClientId(),
                m.getConversation() == null ? null : m.getConversation().getSessionId(),
                m.getQuestion(),
                m.getReponse(),
                m.getSourcesUtilisees() == null ? List.of() : m.getSourcesUtilisees(),
                ChatTraceDocuments.fromTrace(m.getTraceJson(), m.getSourcesUtilisees(), m.getIntention()),
                m.getModel() != null ? m.getModel() : MonitoringKpiService.engineOf(m.getIntention()).toLowerCase(java.util.Locale.ROOT) + "/unspecified",
                m.getTokensIn() != null ? m.getTokensIn() : (m.getQuestion() != null ? m.getQuestion().length() / 4 : 0),
                m.getTokensOut() != null ? m.getTokensOut() : (m.getReponse() != null ? m.getReponse().length() / 4 : 0),
                m.getLatencyMs() != null ? m.getLatencyMs() : 0,
                st,
                asked,
                answered,
                m.getIntention(),
                m.getLangue(),
                m.getDomaine(),
                m.getGrounded(),
                m.getTraceJson() != null && !m.getTraceJson().isBlank()
            );
        }
    }

    public record ExplainRequest(String message, String language) { }

    /**
     * Real-time monitoring feed: in-flight and completed exchanges with tokens,
     * latency, timestamps and the documents consulted.
     *
     * <p>{@code since} (an ISO local date-time) returns only exchanges asked after
     * that instant, which is what lets the console poll every couple of seconds and
     * append rows instead of replacing the table — the difference between a feed
     * that scrolls under the operator's eyes and one that resets their selection.
     * Ordering is by {@code asked_at}, so a row appears the moment the question
     * arrives, marked {@code IN_PROGRESS}, and updates in place when the answer lands.
     */
    @GetMapping("/live-feed")
    @PreAuthorize(READ)
    public List<RealtimeTurn> liveFeed(@RequestParam(defaultValue = "50") int limit,
                                       @RequestParam(required = false)
                                       @org.springframework.format.annotation.DateTimeFormat(iso =
                                               org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
                                       LocalDateTime since) {
        int size = Math.max(1, Math.min(limit, MAX_WINDOW));
        List<MessageEntity> rows = since == null
                ? messages.findLiveFeed(PageRequest.of(0, size))
                : messages.findLiveFeedSince(since, PageRequest.of(0, size));
        return rows.stream().map(RealtimeTurn::from).toList();
    }

    /**
     * The monitoring feed, newest first, optionally for one engine only.
     *
     * <p>Filtering happens over a recent window rather than in SQL: the page shows a
     * window anyway, and reading {@code engine} from the intention means the rule
     * lives in one place instead of a second column that could drift.
     */
    @GetMapping("/turns")
    @PreAuthorize(READ)
    public List<TurnSummary> turns(@RequestParam(defaultValue = "50") int limit,
                                   @RequestParam(defaultValue = "ALL") String engine) {
        return recentTurns(limit, engine);
    }

    private List<TurnSummary> recentTurns(int limit, String engine) {
        int size = Math.max(1, Math.min(limit, MAX_WINDOW));
        String wanted = engine == null ? "ALL" : engine.trim().toUpperCase(java.util.Locale.ROOT);
        List<TurnSummary> rows = messages.findAllByOrderByDateCreationDesc(PageRequest.of(0, MAX_WINDOW))
                .map(TurnSummary::from).getContent().stream()
                .filter(t -> "ALL".equals(wanted) || wanted.equals(t.engine()))
                .toList();
        return rows.size() > size ? rows.subList(0, size) : rows;
    }

    /**
     * Per-engine numbers over the most recent {@code window} exchanges — what the
     * KPI strip shows. Labelled with the window in the UI, because these are the
     * last N turns, not all time.
     */
    @GetMapping("/kpis")
    @PreAuthorize(READ)
    public Map<String, Object> kpis(@RequestParam(defaultValue = "200") int window) {
        int size = Math.max(1, Math.min(window, MAX_WINDOW));
        List<TurnSummary> rows = recentTurns(size, "ALL");
        List<MonitoringKpiService.TurnFact> facts = rows.stream()
                .map(t -> new MonitoringKpiService.TurnFact(t.intention(), t.grounded(),
                        t.sources() != null && !t.sources().isEmpty()))
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("window", size);
        out.put("turns", rows.size());
        out.put("engines", MonitoringKpiService.compute(facts));
        return out;
    }

    @GetMapping("/turns/{id}")
    @PreAuthorize(READ)
    public Map<String, Object> turn(@PathVariable Long id) {
        MessageEntity m = messages.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exchange " + id + " not found"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", TurnSummary.from(m));
        out.put("trace", parseTrace(m.getTraceJson()));
        return out;
    }

    /** Runs the agent pipeline on a question and returns the answer plus its trace. */
    @PostMapping("/explain")
    @PreAuthorize(READ)
    public Map<String, Object> explain(@RequestBody ExplainRequest req) {
        String lang = req.language() == null || req.language().isBlank() ? "auto" : req.language();
        return agent.explain(req.message(), lang);
    }

    private static Map<String, Object> parseTrace(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, MAP);
        } catch (Exception e) {
            return Map.of("error", "unreadable trace");
        }
    }
}
