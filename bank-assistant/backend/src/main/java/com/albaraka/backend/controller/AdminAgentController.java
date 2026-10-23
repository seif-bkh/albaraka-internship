package com.albaraka.backend.controller;

import com.albaraka.backend.dto.AgentAdminDtos.AuditResponse;
import com.albaraka.backend.dto.AgentAdminDtos.EndpointRequest;
import com.albaraka.backend.dto.AgentAdminDtos.EndpointResponse;
import com.albaraka.backend.dto.AgentAdminDtos.HealthResponse;
import com.albaraka.backend.dto.AgentAdminDtos.AnswerRequest;
import com.albaraka.backend.dto.AgentAdminDtos.AnswerResponse;
import com.albaraka.backend.dto.AgentAdminDtos.ChunksSearchRequest;
import com.albaraka.backend.dto.AgentAdminDtos.ChunksSearchResponse;
import com.albaraka.backend.dto.AgentAdminDtos.ReasonRequest;
import com.albaraka.backend.dto.AgentAdminDtos.SearchRequest;
import com.albaraka.backend.dto.AgentAdminDtos.SearchResponse;
import com.albaraka.backend.dto.AgentCorpusDtos.CorpusResponse;
import com.albaraka.backend.dto.AgentCorpusDtos.RebuildRequest;
import com.albaraka.backend.dto.AgentCorpusDtos.RebuildResponse;
import com.albaraka.backend.dto.AgentCorpusDtos;
import com.albaraka.backend.services.AgentAdminAuditService;
import com.albaraka.backend.services.AgentEndpointService;
import com.albaraka.backend.services.CorpusIndexService;
import com.albaraka.backend.services.RagLabServingAgent;
import com.albaraka.backend.services.RagLabClient;
import com.albaraka.backend.services.RagLabViews;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Assistant administration — the "Assistant IA" section of the back-office.
 *
 * <p>Every endpoint uses the scoped View/Run/Manage catalogue. Read-only
 * diagnostics use View, model-backed queries use Run, and high-impact provider,
 * engine, configuration, and full-corpus changes require Manage. Exact quote
 * lookup remains View-only: it makes no model/embedding call and preserves the
 * historical Viewer capability.
 */
@RestController
@RequestMapping("/api/admin/agent")
public class AdminAgentController {

    private static final String READ =
            "hasAnyAuthority('ROLE_SUPER_ADMIN','PERM_RAGLAB_CONSOLE_VIEW','PERM_RAGLAB_CONSOLE_MANAGE')";
    /** Panels that execute model-backed queries without changing console configuration. */
    private static final String RUN =
            "hasAnyAuthority('ROLE_SUPER_ADMIN','PERM_RAGLAB_CONSOLE_RUN','PERM_RAGLAB_CONSOLE_MANAGE')";
    /** Full RAGLab/control-plane management, including engine and provider changes. */
    private static final String MANAGE =
            "hasAnyAuthority('ROLE_SUPER_ADMIN','PERM_RAGLAB_CONSOLE_MANAGE')";

    private final AgentEndpointService endpoints;
    private final RagLabClient raglab;
    private final AgentAdminAuditService audit;
    private final CorpusIndexService corpus;

    public AdminAgentController(AgentEndpointService endpoints, RagLabClient raglab,
                                AgentAdminAuditService audit, CorpusIndexService corpus) {
        this.endpoints = endpoints;
        this.raglab = raglab;
        this.audit = audit;
        this.corpus = corpus;
    }

    /** The effective address and its provenance. */
    @GetMapping("/endpoint")
    @PreAuthorize(READ)
    public EndpointResponse endpoint(Authentication auth) {
        return EndpointResponse.from(endpoints.current(), canManageConsole(auth));
    }

    /** Points the assistant at the RAG service for this environment. */
    @PutMapping("/endpoint")
    @PreAuthorize(MANAGE)
    public EndpointResponse setEndpoint(@RequestBody EndpointRequest request, Authentication auth) {
        return EndpointResponse.from(endpoints.set(request.url(), request.reason(), actor(auth)), true);
    }

    /** Forgets the admin-set address; the deployed default applies again. */
    @DeleteMapping("/endpoint")
    @PreAuthorize(MANAGE)
    public EndpointResponse clearEndpoint(@RequestBody(required = false) ReasonRequest request,
                                          Authentication auth) {
        String reason = request == null ? null : request.reason();
        return EndpointResponse.from(endpoints.clear(reason, actor(auth)), true);
    }

    /**
     * Probe + raw reply for the diagnostic screen. Never throws on an unreachable
     * service: the failure is the payload.
     */
    @GetMapping("/diagnostics")
    @PreAuthorize(READ)
    public HealthResponse diagnostics(Authentication auth) {
        return HealthResponse.from(endpoints.current(), raglab.health(), canManageConsole(auth));
    }

    /** Who changed what on the assistant, newest first. */
    @GetMapping("/audit")
    @PreAuthorize(READ)
    public List<AuditResponse> audit(@RequestParam(defaultValue = "100") int limit,
                                     @RequestParam(defaultValue = "ALL") String action) {
        return audit.recent(limit, action).stream().map(AuditResponse::from).toList();
    }

    /**
     * The action codes actually present in the journal, with their counts — what the
     * filter offers. Read from the journal rather than hardcoded in the page, so a new
     * kind of change appears there the day it is journalled.
     */
    @GetMapping("/audit/actions")
    @PreAuthorize(READ)
    public List<Map<String, Object>> auditActions() {
        return audit.summary();
    }

    /**
     * Exact citation lookup: free and read-only (no model or embedding call),
     * preserving the historical Viewer capability while still protecting source
     * text behind the document/RAGLab view authorities.
     */
    @PostMapping("/chunks/search")
    @PreAuthorize(READ)
    public ChunksSearchResponse searchChunks(@Valid @RequestBody ChunksSearchRequest request) {
        return ChunksSearchResponse.from(raglab.searchChunks(request.text(), request.limit()));
    }

    /** Ranked chunks for a question; one embedding call, no chat model. */
    @PostMapping("/search")
    @PreAuthorize(RUN)
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        RagLabClient.Result result = raglab.search(request.question(), request.k(), request.mode(),
                request.langFilter(), request.neighborRadius());
        journal("CONSOLE_SEARCH", request.question(), result, "récupération");
        return SearchResponse.from(result);
    }

    /** One grounded answer — the screen that spends on an embedding and a chat call. */
    @PostMapping("/answer")
    @PreAuthorize(RUN)
    public AnswerResponse answer(@Valid @RequestBody AnswerRequest request) {
        RagLabClient.Result result = raglab.answer(request.question(), request.k(),
                request.includeExcerpts(), request.queryLang(), request.mode(), request.langFilter());
        journal("CONSOLE_ANSWER", request.question(), result, describe(result));
        return AnswerResponse.from(result);
    }

    /**
     * Console calls that spend model budget are journalled (they are not customer
     * turns, so they do not belong in the chat monitoring). The question is
     * truncated: the journal records the action, not a transcript.
     */
    private void journal(String action, String question, RagLabClient.Result result, String detail) {
        audit.record(action, truncate(question, 120), truncate(detail + " · " + result.latencyMs() + " ms", 600),
                null, currentActor(), "OK");
    }

    /** One-line summary of what a grounded answer came back with. */
    private static String describe(RagLabClient.Result result) {
        RagLabViews.AnswerView view = RagLabViews.answer(result.payload());
        if (view == null) {
            return "réponse illisible";
        }
        return "statut=" + view.status()
                + " · " + view.claimCount() + " affirmation(s) dont " + view.citedClaims() + " sourcée(s)"
                + " · " + (view.sources() == null ? 0 : view.sources().size()) + " source(s)";
    }

    // ------------------------------------------------------------ corpus & index

    /**
     * The files both engines read, and the state of the two chunk worlds built from
     * them. Read-only and cheap: the file world is our own database, the in-house
     * world is the ingestion journal, and the external world is whatever that service
     * reports about its own index.
     */
    @GetMapping("/corpus")
    @PreAuthorize(READ)
    public CorpusResponse corpus() {
        return corpus.corpus();
    }

    /**
     * Records that the external engine's index was rebuilt out of band (D8).
     *
     * <p>Deliberately not a remote trigger: this console has no trustworthy way to
     * start a RAGLab rebuild, and a button that pretends to would be worse than no
     * button. What it can do honestly is keep the record — who rebuilt it, when, why
     * — which is what the corpus screen shows next to the index state RAGLab reports.
     */
    @PostMapping("/corpus/rebuild/raglab")
    @PreAuthorize(MANAGE)
    public RebuildResponse declareRagLabRebuild(@RequestBody(required = false) RebuildRequest request,
                                                Authentication auth) {
        String reason = AgentEndpointService.requireReason(request == null ? null : request.reason());
        audit.record(AgentCorpusDtos.CORPUS_REBUILD_RAGLAB_DECLARED, RagLabServingAgent.PROVIDER,
                "Reconstruction de l'index RAGLab consignée (hors console)", reason, actor(auth), "OK");
        return RebuildResponse.declared(AgentCorpusDtos.CORPUS_REBUILD_RAGLAB_DECLARED);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static String actor(Authentication auth) {
        return auth == null ? null : auth.getName();
    }

    /** Who is acting, for the journal of console calls. */
    private static String currentActor() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    private static boolean canManageConsole(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(granted -> "ROLE_SUPER_ADMIN".equals(granted.getAuthority())
                        || "PERM_RAGLAB_CONSOLE_MANAGE".equals(granted.getAuthority()));
    }
}
