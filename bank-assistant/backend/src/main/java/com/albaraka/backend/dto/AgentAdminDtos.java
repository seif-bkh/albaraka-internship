package com.albaraka.backend.dto;

import com.albaraka.backend.model.AgentAdminAudit;
import com.albaraka.backend.services.AgentEndpointService;
import com.albaraka.backend.services.RagLabClient;
import com.albaraka.backend.services.RagLabViews;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Wire shapes of the assistant administration API ({@code /api/admin/agent/**}).
 *
 * <p>The console shows the effective address together with where it comes from
 * (database, deployed default, or nothing), so an operator never has to guess
 * why the value on screen is what it is.
 */
public final class AgentAdminDtos {

    /** Query languages the service accepts; empty means "let the service decide". */
    public static final String LANG_PATTERN = "|fr|ar|en";
    /** Retrieval modes; empty means "service default". */
    public static final String MODE_PATTERN = "|vector|rrf|blend";

    private AgentAdminDtos() {
    }

    /** Points the assistant at another RAG service; the reason is journalised. */
    public record EndpointRequest(String url, String reason) {
    }

    /** A reason on its own (freeing an address today, switching engines later). */
    public record ReasonRequest(String reason) {
    }

    public record EndpointResponse(String url, String hostPort, String source, String deployedUrl,
                                   boolean configured, String updatedBy, LocalDateTime updatedAt,
                                   boolean editable) {

        public static EndpointResponse from(AgentEndpointService.EndpointView view, boolean editable) {
            return new EndpointResponse(
                    view.url(),
                    view.configured() ? AgentEndpointService.hostPort(view.url()) : null,
                    view.source(),
                    view.deployedUrl(),
                    view.configured(),
                    view.updatedBy(),
                    view.updatedAt(),
                    editable);
        }
    }

    /** The "Santé & diagnostic" payload: the address, the probe, and the raw reply. */
    public record HealthResponse(EndpointResponse endpoint, boolean reachable, Integer httpStatus,
                                 long latencyMs, String error, String errorReason, Object health) {

        public static HealthResponse from(AgentEndpointService.EndpointView view, RagLabClient.Health probe,
                                          boolean editable) {
            return new HealthResponse(EndpointResponse.from(view, editable), probe.reachable(),
                    probe.httpStatus(), probe.latencyMs(), probe.error(), probe.errorReason(),
                    JsonPayload.toPlain(probe.payload()));
        }
    }

    public record AuditResponse(Long id, String action, String target, String detail, String reason,
                                String actor, String outcome, LocalDateTime createdAt) {

        public static AuditResponse from(AgentAdminAudit entry) {
            return new AuditResponse(entry.getId(), entry.getAction(), entry.getTarget(),
                    entry.getDetail(), entry.getReason(), entry.getActor(), entry.getOutcome(),
                    entry.getCreatedAt());
        }
    }

    /** "Could this quote ever validate?" — no model call. */
    public record ChunksSearchRequest(@NotBlank(message = "Le texte à chercher est obligatoire.")
                                      @Size(max = 2000, message = "Le texte ne peut pas dépasser 2000 caractères.")
                                      String text,
                                      @Min(value = 1, message = "Au moins 1 résultat.")
                                      @Max(value = 50, message = "Au plus 50 résultats.")
                                      Integer limit) {
    }

    /** Ranked chunks for a question, no chat model (one embedding call). */
    public record SearchRequest(@NotBlank(message = "La question est obligatoire.")
                                @Size(max = 2000, message = "La question ne peut pas dépasser 2000 caractères.")
                                String question,
                                @Min(value = 1, message = "k est compris entre 1 et 20.")
                                @Max(value = 20, message = "k est compris entre 1 et 20.")
                                Integer k,
                                @Pattern(regexp = MODE_PATTERN, message = "Mode attendu : vector, rrf ou blend.")
                                String mode,
                                @Pattern(regexp = LANG_PATTERN, message = "Langue attendue : fr, ar ou en.")
                                String langFilter,
                                @Min(value = 0, message = "neighbor_radius est compris entre 0 et 2.")
                                @Max(value = 2, message = "neighbor_radius est compris entre 0 et 2.")
                                Integer neighborRadius) {
    }

    /** One grounded answer: embed + chat + claim gate (the screen that costs money). */
    public record AnswerRequest(@NotBlank(message = "La question est obligatoire.")
                                @Size(max = 2000, message = "La question ne peut pas dépasser 2000 caractères.")
                                String question,
                                @Min(value = 1, message = "k est compris entre 1 et 20.")
                                @Max(value = 20, message = "k est compris entre 1 et 20.")
                                Integer k,
                                Boolean includeExcerpts,
                                @Pattern(regexp = LANG_PATTERN, message = "Langue attendue : fr, ar ou en.")
                                String queryLang,
                                @Pattern(regexp = MODE_PATTERN, message = "Mode attendu : vector, rrf ou blend.")
                                String mode,
                                @Pattern(regexp = LANG_PATTERN, message = "Langue attendue : fr, ar ou en.")
                                String langFilter) {
    }

    /** The typed view and the untouched payload, so an unknown shape is still readable. */
    public record AnswerResponse(RagLabViews.AnswerView view, long latencyMs, Object raw) {

        public static AnswerResponse from(RagLabClient.Result result) {
            return new AnswerResponse(RagLabViews.answer(result.payload()), result.latencyMs(),
                    JsonPayload.toPlain(result.payload()));
        }
    }

    public record SearchResponse(RagLabViews.SearchView view, long latencyMs, Object raw) {

        public static SearchResponse from(RagLabClient.Result result) {
            return new SearchResponse(RagLabViews.search(result.payload()), result.latencyMs(),
                    JsonPayload.toPlain(result.payload()));
        }
    }

    public record ChunksSearchResponse(RagLabViews.ChunksView view, long latencyMs, Object raw) {

        public static ChunksSearchResponse from(RagLabClient.Result result) {
            return new ChunksSearchResponse(RagLabViews.chunks(result.payload()), result.latencyMs(),
                    JsonPayload.toPlain(result.payload()));
        }
    }
}
