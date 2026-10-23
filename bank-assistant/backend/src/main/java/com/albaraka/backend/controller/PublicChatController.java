package com.albaraka.backend.controller;

import com.albaraka.backend.dto.ChatMessageHistoryDTO;
import com.albaraka.backend.dto.ChatSessionDTO;
import com.albaraka.backend.dto.PublicChatRequest;
import com.albaraka.backend.dto.PublicChatResponse;
import com.albaraka.backend.model.MessageEntity;
import com.albaraka.backend.model.PublicChatSessionEntity;
import com.albaraka.backend.repository.MessageRepository;
import com.albaraka.backend.repository.PublicChatSessionRepository;
import com.albaraka.backend.security.UserDetailsImpl;
import com.albaraka.backend.services.AgentWire;
import com.albaraka.backend.services.AgentWire.AgentReply;
import com.albaraka.backend.services.AnswerSanitizer;
import com.albaraka.backend.services.ChatPersistenceService;
import com.albaraka.backend.services.ChatTraceDocuments;
import com.albaraka.backend.services.CustomerContextService;
import com.albaraka.backend.services.MonitoringKpiService;
import com.albaraka.backend.services.RefusalHandling;
import com.albaraka.backend.services.RefusalMessage;
import com.albaraka.backend.services.RagLabServingAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public chat entry point: owns session quotas and relays the question to the
 * Python agent.
 *
 * <p>Two-phase metering: a request is only served after the pre-call quota
 * {@code gate()} passes, and the actual usage is {@code settle()}d only after a
 * successful agent reply. Each session's gate→call→settle is serialised with a
 * small striped lock so two rapid messages on the same session cannot both slip
 * past the ceiling (bounded memory, no reliance on {@link String#intern()}). The
 * streaming twin {@code /message/stream} honours the same lock and metering model,
 * so counters can never drift between the two endpoints.
 * <strong>Single-instance scope:</strong> a horizontally-scaled deployment must
 * replace this with a DB-backed optimistic lock on the session row.
 *
 * <p>No {@code @CrossOrigin} here on purpose. Spring Security's CorsFilter runs
 * before MVC, so a handler-level {@code "*"} would be shadowed by the
 * {@code app.cors.allowed-origins} allowlist anyway; origins are configured in
 * one place (SecurityConfig) rather than per controller.
 */
@RestController
@RequestMapping("/api/chat")
public class PublicChatController {

    private static final Logger log = LoggerFactory.getLogger(PublicChatController.class);

    /** Anonymous vs authenticated ceilings, kept together so they can't drift. */
    private static final int MAX_TOKENS_ANONYMOUS = 3000;
    private static final int MAX_MESSAGES_ANONYMOUS = 20;
    private static final int MAX_TOKENS_AUTHENTICATED = 10000;
    private static final int MAX_MESSAGES_AUTHENTICATED = 50;
    private static final int MAX_NEW_SESSIONS_PER_HOUR = 5;
    private static final int SESSION_TITLE_MAX_LENGTH = 50;

    /** How many prior exchanges we pass to the stateless agent as context. */
    private static final int HISTORY_MAX_TURNS = 10;

    /**
     * Bounded striped locks to serialise one session. {@link java.util.concurrent.locks.ReentrantLock}
     * rather than {@code synchronized}: the edge runs on virtual threads (6.3) and a
     * monitor held across the long agent call would pin the carrier thread on JDK 21.
     */
    private static final int SESSION_LOCK_STRIPES = 64;
    private static final java.util.concurrent.locks.ReentrantLock[] SESSION_LOCKS =
            new java.util.concurrent.locks.ReentrantLock[SESSION_LOCK_STRIPES];
    static {
        for (int i = 0; i < SESSION_LOCK_STRIPES; i++) {
            SESSION_LOCKS[i] = new java.util.concurrent.locks.ReentrantLock();
        }
    }

    /** Serialises the compact {@code data: {json}} SSE frames relayed downstream. */
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private PublicChatSessionRepository sessionRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChatPersistenceService chatPersistenceService;

    /**
     * RAGLab serves every turn; the router is the one place that is named, so a
     * admin switching it in the console applies to the next question, not the next
     * deployment.
     */
    @Autowired
    private com.albaraka.backend.services.AgentRouter agentRouter;

    @Autowired
    private com.albaraka.backend.observability.ChatMetrics chatMetrics;

    /**
     * What happens when the serving engine has no grounded answer: one chance at the
     * other engine, then the bank's own refusal wording. Kept in a service because both
     * serving paths (JSON and SSE) must refuse the same way.
     */
    @Autowired
    private RefusalHandling refusalHandling;

    /**
     * Builds the masked, banded picture of the signed-in customer that goes with their
     * question. Anonymous visitors and staff get their question untouched.
     */
    @Autowired
    private CustomerContextService customerContext;

    @PostMapping("/message")
    public ResponseEntity<?> handleMessage(@RequestBody PublicChatRequest request, HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);

        // The client owns the session id (Angular generates a UUID). A missing one
        // is still accepted, but the id is echoed back so the client can adopt it —
        // otherwise every message would open a fresh, unquota'd session.
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
            log.info("Chat session id issued server-side for a new conversation");
        }

        java.util.concurrent.locks.ReentrantLock lock = sessionLock(sessionId);
        lock.lock();
        try {
            return handleMessageLocked(request, ipAddress, sessionId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Runs under a per-session lock. Phase 1 (gate) checks the quota and, if a
     * brand-new anonymous session would be created, the per-IP new-session rate
     * limit. Only then is the agent called; on success Phase 2 (settle) adds the
     * actual token/message usage and Java persists the exchange.
     */
    private ResponseEntity<?> handleMessageLocked(PublicChatRequest request,
                                                  String ipAddress, String sessionId) {
        final PublicChatSessionEntity session;
        PublicChatSessionEntity existing = sessionRepository.findById(sessionId).orElse(null);

        Long currentUserId = currentUserId();

        // Rate limiting: MAX_NEW_SESSIONS_PER_HOUR per IP, anonymous only.
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long recentSessionsCount = sessionRepository.countRecentSessionsByIp(ipAddress, oneHourAgo);

        if (existing == null) {
            if (currentUserId == null && recentSessionsCount >= MAX_NEW_SESSIONS_PER_HOUR) {
                log.warn("New-session rate limit reached for an anonymous client");
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", "Limite de nouvelles sessions atteinte pour cette IP. Veuillez réessayer plus tard."));
            }
            PublicChatSessionEntity created = new PublicChatSessionEntity();
            created.setSessionId(sessionId);
            created.setIpAddress(ipAddress);
            created.setUserId(currentUserId);
            session = created;
        } else if (existing.getUserId() == null && currentUserId != null) {
            // Bind the session to the user the moment they log in mid-conversation.
            existing.setUserId(currentUserId);
            session = existing;
        } else {
            session = existing;
        }

        int maxTokens = currentUserId != null ? MAX_TOKENS_AUTHENTICATED : MAX_TOKENS_ANONYMOUS;
        int maxMessages = currentUserId != null ? MAX_MESSAGES_AUTHENTICATED : MAX_MESSAGES_ANONYMOUS;

        // Phase 1 — pre-call gate.
        if (session.getTokensConsommes() >= maxTokens || session.getNombreMessages() >= maxMessages) {
            chatMetrics.quotaHit("sync");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Limite de session atteinte, merci de rafraichir la page ou de revenir plus tard."));
        }

        org.slf4j.MDC.put(com.albaraka.backend.observability.CorrelationFilter.MDC_SESSION_ID, sessionId);
        long started = System.nanoTime();
        java.time.Instant askedAt = java.time.Instant.now();
        Long exchangeId = null;
        try {
            MessageEntity startedEntity = chatPersistenceService.startExchange(
                    sessionId, request.getLangue(), ipAddress, request.getMessage(),
                    currentClientId(), currentUserId);
            if (startedEntity != null) {
                exchangeId = startedEntity.getId();
            }
        } catch (Exception e) {
            log.error("Failed to start exchange tracking row for session {}", sessionId, e);
        }

        try {
            // Forward to RAGLab, passing recent history.
            List<AgentWire.Turn> history = buildHistory(sessionId);
            String prompt = customerContext.promptFor(currentClientId(), request.getMessage());
            AgentReply reply = agentRouter.invoke(sessionId, prompt,
                    request.getLangue(), history);
            long latencyMs = java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();
            chatMetrics.turn("sync", reply.grounded() ? "grounded" : "refused",
                    java.time.Duration.ofNanos(System.nanoTime() - started), reply.estimatedTokens());

            String rawReponse = reply.text();
            boolean bankRefusal = refusalHandling.replacesEngineProse(reply.intention(), reply.grounded());
            if (bankRefusal) {
                log.info("Served the bank's refusal wording instead of the engine's (session {}, reason {})",
                        sessionId, reply.intention());
            }
            String reponseText = bankRefusal
                    ? RefusalMessage.forLanguage(request.getLangue())
                    : sanitizeForCustomer(rawReponse, sessionId);
            String model = modelFor(reply.traceJson(), reply.intention());
            int tokensIn = request.getMessage() != null ? request.getMessage().length() / 4 : 0;
            int tokensOut = rawReponse != null ? rawReponse.length() / 4 : 0;
            int tokensEstimes = reply.estimatedTokens() > 0 ? reply.estimatedTokens() : (tokensIn + tokensOut);
            String intention = reply.intention();
            boolean ragUtilise = reply.ragUsed();
            List<String> sources = reply.sources();
            LocalDateTime answeredAt = LocalDateTime.now();
            String status = reply.grounded() ? "ANSWERED" : "REFUSED";

            // Phase 2 — settle usage and persist the exchange (single owner).
            try {
                if (exchangeId != null) {
                    chatPersistenceService.completeExchange(
                            exchangeId, intention, reponseText, sources, ragUtilise,
                            reply.grounded(), reply.domain(), reply.traceJson(),
                            model, tokensIn, tokensOut, latencyMs, status, answeredAt);
                } else {
                    chatPersistenceService.recordExchange(
                            sessionId, request.getLangue(), ipAddress,
                            request.getMessage(), null, intention, reponseText, sources, ragUtilise,
                            reply.grounded(), reply.domain(), reply.traceJson(), currentClientId());
                }
            } catch (Exception persistError) {
                // Never fail the chat over a history-write hiccup; log it so an admin
                // can reconcile. The user still gets their answer.
                log.error("Failed to persist chat exchange for session {}", sessionId, persistError);
            }

            session.setTokensConsommes(session.getTokensConsommes() + tokensEstimes);
            session.setNombreMessages(session.getNombreMessages() + 1);
            session.setLangue(request.getLangue());
            sessionRepository.save(session);

            PublicChatResponse response = new PublicChatResponse(
                    reponseText,
                    session.getTokensConsommes(),
                    session.getNombreMessages()
            );
            response.setSessionId(session.getSessionId());
            // Provenance stays server-side on purpose: the admin console reads it from
            // the stored exchange, the customer never receives it.
            response.setGrounded(reply.grounded());
            // The limits travel with the answer so the UI can't disagree with the server.
            response.setTokensLimite(maxTokens);
            response.setMessagesLimite(maxMessages);
            return ResponseEntity.ok(response);

        } catch (org.springframework.web.client.ResourceAccessException e) {
            // Mark exchange failed if row was created
            if (exchangeId != null) {
                chatPersistenceService.completeExchange(
                        exchangeId, "ERROR", "Service IA temporairement indisponible.",
                        List.of(), false, false, "error", null, "engine/unspecified",
                        0, 0, java.time.Duration.ofNanos(System.nanoTime() - started).toMillis(),
                        "FAILED", LocalDateTime.now());
            }
            chatMetrics.turn("sync", "error", java.time.Duration.ofNanos(System.nanoTime() - started), 0);
            log.error("Cannot reach the AI agent (connect/read timeouts)", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Service IA temporairement indisponible. Veuillez réessayer plus tard."));
        } catch (Exception e) {
            if (exchangeId != null) {
                chatPersistenceService.completeExchange(
                        exchangeId, "ERROR", "Erreur lors du traitement de la requête.",
                        List.of(), false, false, "error", null, "engine/unspecified",
                        0, 0, java.time.Duration.ofNanos(System.nanoTime() - started).toMillis(),
                        "FAILED", LocalDateTime.now());
            }
            chatMetrics.turn("sync", "error", java.time.Duration.ofNanos(System.nanoTime() - started), 0);
            log.error("Unexpected failure while handling a chat message", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors du traitement de la requête."));
        }
    }

    /**
     * Server-Sent-Events twin of {@code /message} that relays the agent's token
     * stream to the browser as it is generated.
     *
     * <p>The exchange runs synchronously on the request thread under the same
     * per-session lock as {@code /message}, so gate / settle / persistence behave
     * identically and counters can't drift. A rejection before the reply starts
     * (quota, new-session rate limit) is returned as a normal JSON status; once
     * streaming has begun, failures surface as an SSE {@code error} frame.
     *
     * <p>Each frame is a plain {@code data: {json}} line with a {@code type}
     * discriminator ({@code delta} / {@code error} / {@code done}). The terminal
     * {@code done} frame mirrors {@link PublicChatResponse} (and adds
     * {@code grounded}) so the UI updates counters and content in one place.
     */
    @PostMapping("/message/stream")
    public void streamMessage(@RequestBody PublicChatRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse response) throws IOException {
        String ipAddress = getClientIp(httpRequest);

        // Resolve once; sessionId is assigned exactly once below so it stays
        // effectively final and can be captured by the anonymous StreamListener.
        String requestedSessionId = request.getSessionId();
        final String sessionId;
        if (requestedSessionId == null || requestedSessionId.trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
            log.info("Chat session id issued server-side for a new streaming conversation");
        } else {
            sessionId = requestedSessionId;
        }
        final String lang = request.getLangue();
        final String question = request.getMessage();

        java.util.concurrent.locks.ReentrantLock lock = sessionLock(sessionId);
        lock.lock();
        try {
            // Session resolution + gate, mirroring handleMessageLocked so both
            // endpoints share the same limits, rate limiting and bind-on-login.
            final PublicChatSessionEntity session;
            PublicChatSessionEntity existing = sessionRepository.findById(sessionId).orElse(null);
            Long currentUserId = currentUserId();

            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            long recentSessionsCount = sessionRepository.countRecentSessionsByIp(ipAddress, oneHourAgo);

            if (existing == null) {
                if (currentUserId == null && recentSessionsCount >= MAX_NEW_SESSIONS_PER_HOUR) {
                    writeJsonError(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                            "Limite de nouvelles sessions atteinte pour cette IP. Veuillez réessayer plus tard.");
                    return;
                }
                PublicChatSessionEntity created = new PublicChatSessionEntity();
                created.setSessionId(sessionId);
                created.setIpAddress(ipAddress);
                created.setUserId(currentUserId);
                session = created;
            } else if (existing.getUserId() == null && currentUserId != null) {
                existing.setUserId(currentUserId);
                session = existing;
            } else {
                session = existing;
            }

            int maxTokens = currentUserId != null ? MAX_TOKENS_AUTHENTICATED : MAX_TOKENS_ANONYMOUS;
            int maxMessages = currentUserId != null ? MAX_MESSAGES_AUTHENTICATED : MAX_MESSAGES_ANONYMOUS;

            if (session.getTokensConsommes() >= maxTokens || session.getNombreMessages() >= maxMessages) {
                chatMetrics.quotaHit("stream");
                writeJsonError(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Limite de session atteinte, merci de rafraichir la page ou de revenir plus tard.");
                return;
            }
            org.slf4j.MDC.put(com.albaraka.backend.observability.CorrelationFilter.MDC_SESSION_ID, sessionId);
            final long started = System.nanoTime();
            final Long currentUid = currentUserId;
            final Long currentCid = currentClientId();
            Long initialExchangeId = null;
            try {
                MessageEntity startedEntity = chatPersistenceService.startExchange(
                        sessionId, lang, ipAddress, question, currentCid, currentUid);
                if (startedEntity != null) {
                    initialExchangeId = startedEntity.getId();
                }
            } catch (Exception e) {
                log.error("Failed to start exchange tracking row for session {}", sessionId, e);
            }
            final Long exchangeId = initialExchangeId;

            // Gate passed — commit the SSE response and relay the agent stream.
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            // Never let an intermediary buffer chunks; the client wants them live.
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("X-Accel-Buffering", "no");
            PrintWriter out = response.getWriter();

            try {
                List<AgentWire.Turn> history = buildHistory(sessionId);
                agentRouter.invokeStream(sessionId, customerContext.promptFor(currentClientId(), question), lang,
                        history, new AgentWire.StreamListener() {

                            /**
                             * Citation markers can straddle two streamed chunks, so the
                             * tail is held back briefly and only the safe prefix is relayed.
                             * The terminal {@code done} frame carries the fully sanitised
                             * answer, which the client adopts as the bubble content.
                             */
                            private final AnswerSanitizer.Stream scrubber = new AnswerSanitizer.Stream();

                            @Override
                            public void onDelta(String text) {
                                String safe = scrubber.accept(text);
                                if (!safe.isEmpty()) {
                                    writeSse(out, Map.of("type", "delta", "text", safe));
                                }
                            }

                            @Override
                            public void onDone(AgentWire.StreamDone done) {
                                String tail = scrubber.flush();
                                if (tail != null && !tail.isEmpty()) {
                                    writeSse(out, Map.of("type", "delta", "text", tail));
                                }
                                long latencyMs = java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();
                                chatMetrics.turn("stream", done.grounded() ? "grounded" : "refused",
                                        java.time.Duration.ofNanos(System.nanoTime() - started),
                                        (done.text().length() / 4) + (question.length() / 4));
                                settleAndEmitDone(out, session, sessionId, lang,
                                        ipAddress, question, done, maxTokens, maxMessages, exchangeId,
                                        latencyMs);
                            }

                            @Override
                            public void onError(Exception error) {
                                long latencyMs = java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();
                                if (exchangeId != null) {
                                    chatPersistenceService.completeExchange(
                                            exchangeId, "ERROR", "Service IA temporairement indisponible.",
                                            List.of(), false, false, "error", null, "engine/unspecified",
                                            0, 0, latencyMs, "FAILED", LocalDateTime.now());
                                }
                                chatMetrics.turn("stream", "error",
                                        java.time.Duration.ofNanos(System.nanoTime() - started), 0);
                                log.error("Agent stream error for session {}", sessionId, error);
                                writeSse(out, Map.of("type", "error", "message",
                                        "Service IA temporairement indisponible. Veuillez réessayer plus tard."));
                            }
                        });
            } finally {
                out.flush();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Persists the exchange, settles session usage and writes the terminal
     * {@code done} frame (a superset of {@link PublicChatResponse}).
     */
    private void settleAndEmitDone(PrintWriter out, PublicChatSessionEntity session,
                                   String sessionId, String lang, String ipAddress,
                                   String question, AgentWire.StreamDone done,
                                   int maxTokens, int maxMessages, Long exchangeId, long latencyMs) {
        AgentWire.StreamDone settled = done;
        String intention = stringOrNull(done.structured().get("intent"));
        String rawReponse = settled.text();
        // A gap in one engine's evidence is not something to describe to a customer in
        // that engine's words ("the supplied documents", and the list it appended to
        // them): the bank's own sentence is served, in the customer's language.
        boolean bankRefusal = refusalHandling.replacesEngineProse(intention, settled.grounded());
        if (bankRefusal) {
            log.info("Served the bank's refusal wording instead of the engine's (session {}, reason {})",
                    sessionId, intention);
        }
        String reponseText = bankRefusal
                ? RefusalMessage.forLanguage(lang)
                : sanitizeForCustomer(rawReponse, sessionId);
        String model = modelFor(settled.traceJson(), intention);
        List<String> sources = settled.sources();
        boolean ragUtilise = !sources.isEmpty();
        int tokensIn = question.length() / 4;
        int tokensOut = rawReponse.length() / 4;
        int tokensEstimes = tokensIn + tokensOut;
        String status = settled.grounded() ? "ANSWERED" : "REFUSED";

        try {
            if (exchangeId != null) {
                chatPersistenceService.completeExchange(
                        exchangeId, intention, reponseText, sources, ragUtilise,
                        settled.grounded(), stringOrNull(settled.structured().get("domain")), settled.traceJson(),
                        model, tokensIn, tokensOut, latencyMs, status, LocalDateTime.now());
            } else {
                chatPersistenceService.recordExchange(sessionId, lang, ipAddress,
                        question, null, intention, reponseText, sources, ragUtilise,
                        settled.grounded(), stringOrNull(settled.structured().get("domain")), settled.traceJson(),
                        currentClientId());
            }
        } catch (Exception persistError) {
            log.error("Failed to persist streamed chat exchange for session {}", sessionId, persistError);
        }

        session.setTokensConsommes(session.getTokensConsommes() + tokensEstimes);
        session.setNombreMessages(session.getNombreMessages() + 1);
        session.setLangue(lang);
        sessionRepository.save(session);

        Map<String, Object> frame = new java.util.LinkedHashMap<>();
        frame.put("type", "done");
        frame.put("grounded", settled.grounded());
        frame.put("reponse", reponseText);
        frame.put("sessionId", session.getSessionId());
        frame.put("tokensConsommes", session.getTokensConsommes());
        frame.put("tokensLimite", maxTokens);
        frame.put("nombreMessages", session.getNombreMessages());
        frame.put("messagesLimite", maxMessages);
        writeSse(out, frame);
    }

    /** Writes one {@code data: {json}\n\n} frame; {@code PrintWriter} never throws. */
    private void writeSse(PrintWriter out, Map<String, Object> event) {
        try {
            out.write("data: ");
            out.write(JSON.writeValueAsString(event));
            out.write("\n\n");
            out.flush();
        } catch (IOException e) {
            // Client disconnected; PrintWriter swallows errors so this is rare.
            log.debug("SSE write aborted (client gone): {}", e.getMessage());
        }
    }

    private void writeJsonError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.writeValueAsString(Map.of("error", message)));
        response.getWriter().flush();
    }

    private static java.util.concurrent.locks.ReentrantLock sessionLock(String sessionId) {
        return SESSION_LOCKS[(sessionId.hashCode() & 0x7fffffff) % SESSION_LOCK_STRIPES];
    }

    /**
     * The answer as the customer may read it: same words, no provenance. A stripped
     * answer is logged, because a leak means the generator prompt stopped holding the
     * model to plain prose — worth noticing before it becomes a habit.
     */
    private String sanitizeForCustomer(String raw, String sessionId) {
        String clean = AnswerSanitizer.sanitize(raw);
        if (raw != null && !raw.isBlank() && !raw.strip().equals(clean)) {
            log.info("Provenance stripped from an answer before delivery (session {})", sessionId);
        }
        return clean;
    }

    /**
     * The model that answered, for the admin's audit row. The in-house pipeline names
     * it in its trace; an engine that does not name one is recorded as the engine,
     * because "we do not know which model" is a fact the console should show rather
     * than a default string that implies it does.
     */
    private String modelFor(String traceJson, String intention) {
        String model = ChatTraceDocuments.modelFromTrace(traceJson);
        if (model != null && !model.isBlank()) {
            return model;
        }
        // There is one engine; "unspecified" is the honest part — RAGLab did not name
        // the model in its trace for this turn.
        return RagLabServingAgent.PROVIDER + "/unspecified";
    }

    private String stringOrNull(Object o) {
        return o == null ? null : o.toString();
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        return null;
    }

    /**
     * The authenticated bank client's id (clients table) when the caller carries
     * {@code ROLE_UTILISATEUR}, else {@code null} — staff and anonymous sessions
     * must never claim a conversation.
     */
    private Long currentClientId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            return null;
        }
        boolean clientRole = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_UTILISATEUR".equals(a.getAuthority()));
        return clientRole ? userDetails.getId() : null;
    }

    /**
     * The last {@link #HISTORY_MAX_TURNS} prior exchanges as a flat user/assistant
     * turn list, oldest first, for the stateless agent. Each persisted exchange row
     * holds both halves, so one row yields a user turn then an assistant turn.
     */
    private List<AgentWire.Turn> buildHistory(String sessionId) {
        List<AgentWire.Turn> turns = new ArrayList<>();
        List<MessageEntity> last = chatPersistenceService.lastTurns(sessionId, HISTORY_MAX_TURNS);
        for (MessageEntity m : last) {
            if (m.getQuestion() != null && !m.getQuestion().isEmpty()) {
                turns.add(new AgentWire.Turn("user", m.getQuestion()));
            }
            if (m.getReponse() != null && !m.getReponse().isEmpty()) {
                turns.add(new AgentWire.Turn("assistant", m.getReponse()));
            }
        }
        return turns;
    }

    /**
     * Left-most X-Forwarded-For entry is the client as seen by our nginx. Only
     * trusted when the request genuinely came through a proxy: a client can
     * otherwise spoof the header and reset its own rate limit.
     */
    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || !xfHeader.contains(request.getRemoteAddr())) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> getUserSessions() {
        Long currentUserId = currentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utilisateur non authentifié."));
        }

        List<PublicChatSessionEntity> sessions =
                sessionRepository.findByUserIdOrderByDateDerniereActiviteDesc(currentUserId);

        // One query per session (findFirstQuestionBySessionId) — acceptable while a
        // user has a handful of sessions; batch it if this list ever grows.
        List<ChatSessionDTO> sessionDTOs = new ArrayList<>();
        for (PublicChatSessionEntity session : sessions) {
            String firstQuestion = messageRepository.findFirstQuestionBySessionId(session.getSessionId());
            String titre = firstQuestion != null ? firstQuestion : "Nouvelle conversation";
            if (titre.length() > SESSION_TITLE_MAX_LENGTH) {
                titre = titre.substring(0, SESSION_TITLE_MAX_LENGTH - 3) + "...";
            }
            sessionDTOs.add(new ChatSessionDTO(session.getSessionId(), session.getDateCreation(), titre));
        }

        return ResponseEntity.ok(sessionDTOs);
    }

    @GetMapping("/history")
    public ResponseEntity<?> getChatHistory(@RequestParam(required = false) String sessionId) {
        Long currentUserId = currentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utilisateur non authentifié."));
        }

        List<MessageEntity> messages;
        if (sessionId != null && !sessionId.isEmpty()) {
            PublicChatSessionEntity session = sessionRepository.findById(sessionId).orElse(null);
            if (session == null || !currentUserId.equals(session.getUserId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Accès refusé à cette session."));
            }
            messages = messageRepository.findMessagesBySessionIdOrderByDateCreationAsc(sessionId);
        } else {
            messages = messageRepository.findMessagesByUserIdOrderByDateCreationAsc(currentUserId);
        }

        // One row holds both halves of an exchange, so both DTOs share a timestamp.
        // Order within the pair is fixed by construction here, not by the sort.
        List<ChatMessageHistoryDTO> history = new ArrayList<>();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        for (MessageEntity m : messages) {
            String timeStr = m.getDateCreation() != null ? m.getDateCreation().format(timeFormatter) : "";

            if (m.getQuestion() != null && !m.getQuestion().isEmpty()) {
                history.add(new ChatMessageHistoryDTO("user", m.getQuestion(), timeStr, m.getDateCreation(), 0));
            }
            if (m.getReponse() != null && !m.getReponse().isEmpty()) {
                // Read-time sanitising, not only write-time: rows stored before the
                // no-provenance policy still hold the engine's wording (and, on a
                // refusal, sometimes the list of documents it read). The admin console
                // reads the same rows unsanitised — the reviewer channel keeps the truth.
                history.add(new ChatMessageHistoryDTO("assistant",
                        AnswerSanitizer.sanitizeStored(m.getReponse()), timeStr, m.getDateCreation(), 0));
            }
        }

        return ResponseEntity.ok(history);
    }
}
