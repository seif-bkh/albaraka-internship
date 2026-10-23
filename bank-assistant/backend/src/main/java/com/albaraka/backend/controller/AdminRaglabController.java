package com.albaraka.backend.controller;

import com.albaraka.backend.dto.JsonPayload;
import com.albaraka.backend.services.AgentAdminAuditService;
import com.albaraka.backend.services.AgentEndpointService;
import com.albaraka.backend.services.RagLabClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RAGLab agent console controller — BFF proxying the RAGLab service directly.
 *
 * <p>Fronts all capabilities defined in the RAGLab specification:
 * models, chunking, retrieval, corpus inspect, API keys, documents feed,
 * indexing, evaluations, and diagnostics.
 */
@RestController
@RequestMapping("/api/admin/raglab")
public class AdminRaglabController {

    private static final String READ =
            "hasAnyAuthority('ROLE_SUPER_ADMIN','PERM_RAGLAB_CONSOLE_VIEW','PERM_RAGLAB_CONSOLE_MANAGE')";
    private static final String RUN =
            "hasAnyAuthority('ROLE_SUPER_ADMIN','PERM_RAGLAB_CONSOLE_RUN','PERM_RAGLAB_CONSOLE_MANAGE')";
    private static final String MANAGE =
            "hasAnyAuthority('ROLE_SUPER_ADMIN','PERM_RAGLAB_CONSOLE_MANAGE')";
    /**
     * RAGLab ingestion is Manage-only for dynamic roles. The historical
     * ADMIN_DOCUMENTS system role is grandfathered for this exact operation,
     * but only while it retains its migrated Documents Process grant.
     */
    private static final String RAGLAB_INGESTION_PROCESS =
            "hasAnyAuthority('ROLE_SUPER_ADMIN','PERM_RAGLAB_CONSOLE_MANAGE')"
                    + " or (hasAuthority('ROLE_ADMIN_DOCUMENTS') and hasAuthority('PERM_RAGLAB_CONSOLE_VIEW')"
                    + " and hasAuthority('PERM_DOCUMENTS_PROCESS'))";
    private static final String RAGLAB_INGESTION_STATUS_READ =
            "hasAnyAuthority('ROLE_SUPER_ADMIN','PERM_RAGLAB_CONSOLE_VIEW','PERM_RAGLAB_CONSOLE_MANAGE')";
    private static final String RAGLAB_FEED_READ =
            "hasAnyAuthority('ROLE_SUPER_ADMIN','PERM_RAGLAB_CONSOLE_VIEW','PERM_RAGLAB_CONSOLE_MANAGE')";
    /** Preserve the old system role's feed editor access without exposing the feed to new Documents-only roles. */
    private static final String RAGLAB_FEED_CREATE =
            "hasAnyAuthority('ROLE_SUPER_ADMIN','PERM_RAGLAB_CONSOLE_MANAGE')"
                    + " or (hasAuthority('ROLE_ADMIN_DOCUMENTS') and hasAuthority('PERM_RAGLAB_CONSOLE_VIEW')"
                    + " and hasAuthority('PERM_DOCUMENTS_CREATE'))";
    private static final String RAGLAB_FEED_DELETE =
            "hasAnyAuthority('ROLE_SUPER_ADMIN','PERM_RAGLAB_CONSOLE_MANAGE')"
                    + " or (hasAuthority('ROLE_ADMIN_DOCUMENTS') and hasAuthority('PERM_RAGLAB_CONSOLE_VIEW')"
                    + " and hasAuthority('PERM_DOCUMENTS_DELETE'))";

    private final RagLabClient raglab;
    private final AgentAdminAuditService audit;

    public AdminRaglabController(RagLabClient raglab, AgentAdminAuditService audit) {
        this.raglab = raglab;
        this.audit = audit;
    }

    /** Live status and health state machine for the top status bar. */
    @GetMapping("/health")
    @PreAuthorize(READ)
    public ResponseEntity<Object> health() {
        RagLabClient.Health health = raglab.health();
        if (health.reachable() && health.payload() != null) {
            return ResponseEntity.ok(JsonPayload.toPlain(health.payload()));
        }
        Map<String, Object> down = new LinkedHashMap<>();
        down.put("status", "down");
        down.put("error", health.error());
        down.put("error_reason", health.errorReason());
        down.put("latency_ms", health.latencyMs());
        return ResponseEntity.ok(down);
    }

    /** Models and providers registry. */
    @GetMapping("/models")
    @PreAuthorize(READ)
    public ResponseEntity<Object> models() {
        RagLabClient.Result res = raglab.models();
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Read active profile configuration. */
    @GetMapping("/profile")
    @PreAuthorize(READ)
    public ResponseEntity<Object> profile() {
        RagLabClient.Result res = raglab.profile();
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Update active profile configuration (models, chunking, retrieval). */
    @PostMapping("/profile")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Object> updateProfile(@RequestBody Map<String, Object> body, Authentication auth) {
        RagLabClient.Result res = raglab.updateProfile(body);
        audit.record("RAGLAB_PROFILE_SET", "profile", truncate(body.keySet().toString(), 300),
                null, actor(auth), "OK");
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** List registered keys and masked status; key mutations remain Manage-only. */
    @GetMapping("/keys")
    @PreAuthorize(READ)
    public ResponseEntity<Object> keys() {
        RagLabClient.Result res = raglab.keys();
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Set an API key (never written to audit or logs). */
    @PostMapping("/keys")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Object> setKey(@RequestBody Map<String, Object> body, Authentication auth) {
        String keyEnv = body.get("key_env") != null ? String.valueOf(body.get("key_env")) : "unknown";
        RagLabClient.Result res = raglab.setKey(body);
        audit.record("RAGLAB_KEY_SET", keyEnv, "Clé mise à jour", null, actor(auth), "OK");
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Delete an API key. */
    @DeleteMapping("/keys/{env}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Object> deleteKey(@PathVariable String env,
                                            @RequestParam(required = false, defaultValue = "false") Boolean persist,
                                            Authentication auth) {
        RagLabClient.Result res = raglab.deleteKey(env, persist);
        audit.record("RAGLAB_KEY_DELETE", env, "Clé supprimée", null, actor(auth), "OK");
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Inspect corpus and chunk statistics. */
    @GetMapping("/inspect")
    @PreAuthorize(READ)
    public ResponseEntity<Object> inspect(@RequestParam(required = false, defaultValue = "5") Integer limit) {
        RagLabClient.Result res = raglab.inspect(limit);
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Exact citation lookup: no model call; available with RAGLab View as before. */
    @PostMapping("/chunks/search")
    @PreAuthorize(READ)
    public ResponseEntity<Object> searchChunks(@RequestBody Map<String, Object> body) {
        String text = body.get("text") != null ? String.valueOf(body.get("text")) : "";
        Integer limit = body.get("limit") instanceof Number ? ((Number) body.get("limit")).intValue() : null;
        RagLabClient.Result res = raglab.searchChunks(text, limit);
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Ranked retrieval hits for a question. */
    @PostMapping("/search")
    @PreAuthorize(RUN)
    public ResponseEntity<Object> search(@RequestBody Map<String, Object> body, Authentication auth) {
        String question = body.get("question") != null ? String.valueOf(body.get("question")) : "";
        Integer k = body.get("k") instanceof Number ? ((Number) body.get("k")).intValue() : null;
        String mode = body.get("mode") != null ? String.valueOf(body.get("mode")) : null;
        String langFilter = body.get("lang_filter") != null ? String.valueOf(body.get("lang_filter")) : null;
        Integer neighborRadius = body.get("neighbor_radius") instanceof Number
                ? ((Number) body.get("neighbor_radius")).intValue() : null;

        RagLabClient.Result res = raglab.search(question, k, mode, langFilter, neighborRadius);
        audit.record("RAGLAB_CONSOLE_SEARCH", truncate(question, 100),
                "hits=" + (res.payload() != null && res.payload().has("hits") ? res.payload().get("hits").size() : 0),
                null, actor(auth), "OK");
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Grounded answer endpoint (Ask product screen). */
    @PostMapping("/answer")
    @PreAuthorize(RUN)
    public ResponseEntity<Object> answer(@RequestBody Map<String, Object> body, Authentication auth) {
        String question = body.get("question") != null ? String.valueOf(body.get("question")) : "";
        Integer k = body.get("k") instanceof Number ? ((Number) body.get("k")).intValue() : null;
        Boolean includeExcerpts = body.get("include_excerpts") instanceof Boolean
                ? (Boolean) body.get("include_excerpts") : null;
        String queryLang = body.get("query_lang") != null ? String.valueOf(body.get("query_lang")) : null;
        String mode = body.get("mode") != null ? String.valueOf(body.get("mode")) : null;
        String langFilter = body.get("lang_filter") != null ? String.valueOf(body.get("lang_filter")) : null;

        RagLabClient.Result res = raglab.answer(question, k, includeExcerpts, queryLang, mode, langFilter);
        audit.record("RAGLAB_CONSOLE_ANSWER", truncate(question, 100),
                "latence=" + res.latencyMs() + "ms", null, actor(auth), "OK");
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Embedding cross-lingual sanity test. */
    @PostMapping("/embeddings/sanity")
    @PreAuthorize(RUN)
    public ResponseEntity<Object> embeddingsSanity(Authentication auth) {
        RagLabClient.Result res = raglab.embeddingsSanity();
        audit.record("RAGLAB_SANITY_TEST", "embeddings", "Test de cosinus cross-lingues",
                null, actor(auth), "OK");
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Start background ingestion. */
    @PostMapping("/ingest")
    @PreAuthorize(RAGLAB_INGESTION_PROCESS)
    public ResponseEntity<Object> ingest(@RequestParam(required = false, defaultValue = "false") Boolean reset,
                                         Authentication auth) {
        RagLabClient.Result res = raglab.ingest(reset);
        audit.record("RAGLAB_INGEST_START", Boolean.TRUE.equals(reset) ? "RESET_AND_REBUILD" : "INCREMENTAL",
                "Lancement indexation RAGLab", null, actor(auth), "OK");
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Current ingestion job status. */
    @GetMapping("/ingest/status")
    @PreAuthorize(RAGLAB_INGESTION_STATUS_READ)
    public ResponseEntity<Object> ingestStatus() {
        RagLabClient.Result res = raglab.ingestStatus();
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** List pushed documents in the feed. */
    @GetMapping("/documents")
    @PreAuthorize(RAGLAB_FEED_READ)
    public ResponseEntity<Object> documents() {
        RagLabClient.Result res = raglab.documents();
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Get single pushed document metadata. */
    @GetMapping("/documents/{id}")
    @PreAuthorize(RAGLAB_FEED_READ)
    public ResponseEntity<Object> document(@PathVariable String id) {
        RagLabClient.Result res = raglab.document(id);
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Push a document (JSON payload). */
    @PostMapping("/documents")
    @PreAuthorize(RAGLAB_FEED_CREATE)
    public ResponseEntity<Object> pushDocumentJson(@RequestBody Map<String, Object> body,
                                                   @RequestParam(required = false) String id,
                                                   @RequestParam(required = false, defaultValue = "false") Boolean index,
                                                   Authentication auth) {
        requireProcessPermission(index, auth);
        String docId = id != null ? id : (body.get("id") != null ? String.valueOf(body.get("id")) : null);
        RagLabClient.Result res = raglab.pushDocument(docId, index, body);
        audit.record("RAGLAB_DOC_PUSH", docId != null ? docId : "batch",
                "index=" + index, null, actor(auth), "OK");
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Push a document via multipart file upload. */
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(RAGLAB_FEED_CREATE)
    public ResponseEntity<Object> pushDocumentMultipart(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(required = false) String id,
                                                        @RequestParam(required = false, defaultValue = "false") Boolean index,
                                                        Authentication auth) throws IOException {
        requireProcessPermission(index, auth);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.txt";
        String effectiveId = id != null && !id.isBlank() ? id : sanitizeDocId(filename);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", effectiveId);
        body.put("filename", filename);
        body.put("content", Base64.getEncoder().encodeToString(file.getBytes()));
        body.put("content_encoding", "base64");

        RagLabClient.Result res = raglab.pushDocument(effectiveId, index, body);
        audit.record("RAGLAB_DOC_UPLOAD", effectiveId, filename + " (" + file.getSize() + " o)",
                null, actor(auth), "OK");
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    private void requireProcessPermission(Boolean index, Authentication auth) {
        if (!Boolean.TRUE.equals(index)) return;
        boolean isSuperAdminOrRaglabManager = auth != null && auth.getAuthorities().stream()
                .anyMatch(granted -> "ROLE_SUPER_ADMIN".equals(granted.getAuthority())
                        || "PERM_RAGLAB_CONSOLE_MANAGE".equals(granted.getAuthority()));
        boolean isGrandfatheredDocumentManager = auth != null
                && auth.getAuthorities().stream().anyMatch(granted ->
                        "ROLE_ADMIN_DOCUMENTS".equals(granted.getAuthority()))
                && auth.getAuthorities().stream().anyMatch(granted ->
                        "PERM_RAGLAB_CONSOLE_VIEW".equals(granted.getAuthority()))
                && auth.getAuthorities().stream().anyMatch(granted ->
                        "PERM_DOCUMENTS_PROCESS".equals(granted.getAuthority()));
        boolean allowed = isSuperAdminOrRaglabManager || isGrandfatheredDocumentManager;
        if (!allowed) {
            throw new AccessDeniedException(
                    "RAGLAB_CONSOLE_MANAGE is required to index a RAGLab feed document.");
        }
    }

    /** Delete a document and purge chunks from all collections immediately. */
    @DeleteMapping("/documents/{id}")
    @PreAuthorize(RAGLAB_FEED_DELETE)
    public ResponseEntity<Object> deleteDocument(@PathVariable String id, Authentication auth) {
        RagLabClient.Result res = raglab.deleteDocument(id);
        audit.record("RAGLAB_DOC_DELETE", id, "Purge immédiate des chunks", null, actor(auth), "OK");
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Run an evaluation against a question set. */
    @PostMapping("/evaluate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Object> evaluate(@RequestBody Map<String, Object> body, Authentication auth) {
        RagLabClient.Result res = raglab.evaluate(body);
        audit.record("RAGLAB_EVALUATE", String.valueOf(body.get("questions")),
                "top_k=" + body.get("top_k"), null, actor(auth), "OK");
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Run synchronous 50-question offline harness. */
    @PostMapping("/diagnostics/harness50")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Object> harness50(Authentication auth) {
        RagLabClient.Result res = raglab.diagnosticsHarness50();
        audit.record("RAGLAB_HARNESS50", "harness50", "Diagnostic offline", null, actor(auth), "OK");
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    /** Live catalog snapshot from xKiro gateway. */
    @PostMapping("/diagnostics/catalog")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Object> catalog(Authentication auth) {
        RagLabClient.Result res = raglab.diagnosticsCatalog();
        audit.record("RAGLAB_CATALOG", "xkiro", "Inspection catalogue", null, actor(auth), "OK");
        return ResponseEntity.ok(JsonPayload.toPlain(res.payload()));
    }

    private static String actor(Authentication auth) {
        return auth == null ? null : auth.getName();
    }

    private static String truncate(String val, int max) {
        if (val == null) return null;
        return val.length() <= max ? val : val.substring(0, max - 1) + "…";
    }

    private static String sanitizeDocId(String filename) {
        if (filename == null) return "doc";
        String base = filename.replaceAll("\\.[^.]+$", "");
        String safe = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isEmpty() || !Character.isLetterOrDigit(safe.charAt(0))) {
            safe = "doc_" + safe;
        }
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }
}
