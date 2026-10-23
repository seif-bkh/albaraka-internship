package com.albaraka.backend.dto;

import com.albaraka.backend.model.Document;

import java.util.List;
import java.util.Map;

/**
 * Corpus & index (C4): the files the bank has published, and the state of the
 * one index built from them.
 *
 * <p>RAGLab is the only engine, and it owns its own index. So the payload keeps
 * `shared` (our own record of the files) and `raglab` (what that service reports
 * about itself) apart instead of merging them, and carries an explicit
 * {@code rebuiltManually} marker instead of a rebuild this console cannot run.
 */
public final class AgentCorpusDtos {

    private AgentCorpusDtos() {
    }

    /** The shared files: what the drop zone published, before any engine cuts it up. */
    public record SharedCorpus(long total, long published, long archived, long indexed,
                               long indexing, long failed, List<FileRow> files) {
    }

    public record FileRow(Long id, String name, String type, Long sizeBytes, String status,
                          String indexStatus, String indexError, String version, String language,
                          String depositedAt) {

        public static FileRow from(Document d) {
            return new FileRow(d.getId(), d.getNomFichier(), d.getType(), d.getTailleFichier(),
                    d.getStatut() == null ? null : d.getStatut().name(),
                    d.getStatutIndexation() == null ? null : d.getStatutIndexation().name(),
                    d.getMessageErreurIndexation(), d.getVersion(), d.getLangue(),
                    d.getDateDepot() == null ? null : d.getDateDepot().toString());
        }
    }

    /**
     * The external engine's world, as it reports it.
     *
     * <p>Every fact here is optional on purpose: whatever the service says about its
     * index is surfaced as-is, and what it does not say stays null rather than being
     * guessed. {@code rebuiltManually} is the honest half — this engine is rebuilt
     * out of band (D8), so the console can only record that the operator did it.
     */
    public record RagLabIndex(boolean configured, boolean reachable, String detail,
                              String reportedBy, Long documents, Long chunks, Long embeddings,
                              String embeddingModel, String indexedAt, boolean rebuiltManually,
                              String declaredBy, String declaredAt, String declaredReason,
                              Map<String, Object> raw) {
    }

    /** Everything C4 shows, in one read. */
    public record CorpusResponse(SharedCorpus shared, RagLabIndex raglab) {
    }

    public record RebuildRequest(String reason) {
    }

    /** What a rebuild actually did — accepted vs skipped, per document. */
    public record RebuildResponse(String engine, int accepted, List<Long> acceptedIds,
                                 List<Skipped> skipped, String message, String journalled) {

        public static RebuildResponse declared(String journalled) {
            return new RebuildResponse("RAGLAB", 0, List.of(), List.of(),
                    "Reconstruction RAGLab consignée. Elle se fait hors de cette console (D8) ; "
                            + "l'index de RAGLab n'est pas reconstruit par ce bouton.",
                    journalled);
        }
    }

    /** A file the rebuild could not queue, and why. */
    public record Skipped(Long id, String name, String why) {
    }

    public static final String CORPUS_REBUILD = "CORPUS_REBUILD";
    public static final String CORPUS_REBUILD_RAGLAB_DECLARED = "CORPUS_REBUILD_RAGLAB_DECLARED";

}
