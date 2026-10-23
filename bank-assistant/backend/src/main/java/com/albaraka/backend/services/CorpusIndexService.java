package com.albaraka.backend.services;

import com.albaraka.backend.dto.AgentCorpusDtos.CorpusResponse;
import com.albaraka.backend.dto.AgentCorpusDtos.FileRow;
import com.albaraka.backend.dto.AgentCorpusDtos.RagLabIndex;
import com.albaraka.backend.dto.AgentCorpusDtos.SharedCorpus;
import com.albaraka.backend.model.AgentAdminAudit;
import com.albaraka.backend.model.Document;
import com.albaraka.backend.model.StatutDocument;
import com.albaraka.backend.model.StatutIndexation;
import com.albaraka.backend.repository.AgentAdminAuditRepository;
import com.albaraka.backend.repository.DocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Corpus & index (C4): the published files, and the one index RAGLab builds.
 *
 * <p>The two are never blended: the file list is our own database, the index is
 * whatever RAGLab reports about itself — with the honest gap that its rebuild
 * happens out of band (D8), so the console records the operator's declaration
 * instead of pretending to trigger it.
 */
@Service
public class CorpusIndexService {

    private final DocumentRepository documents;
    private final AgentEndpointService endpoints;
    private final RagLabClient raglab;
    private final AgentAdminAuditRepository audit;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> RAW = new TypeReference<>() { };

    public CorpusIndexService(DocumentRepository documents, AgentEndpointService endpoints,
                              RagLabClient raglab, AgentAdminAuditRepository audit) {
        this.documents = documents;
        this.endpoints = endpoints;
        this.raglab = raglab;
        this.audit = audit;
    }

    public CorpusResponse corpus() {
        List<Document> all = documents.findAll(Sort.by(Sort.Direction.DESC, "dateDepot"));
        return new CorpusResponse(shared(all), raglab());
    }

    // ------------------------------------------------------------------ file world

    private SharedCorpus shared(List<Document> all) {
        long published = all.stream().filter(d -> d.getStatut() == StatutDocument.VALIDE).count();
        long archived = all.stream().filter(d -> d.getStatut() == StatutDocument.ARCHIVE).count();
        long indexed = count(all, StatutIndexation.INDEXE);
        long indexing = count(all, StatutIndexation.EN_COURS);
        long failed = count(all, StatutIndexation.ERREUR);
        List<FileRow> rows = all.stream().map(FileRow::from).toList();
        return new SharedCorpus(all.size(), published, archived, indexed, indexing, failed, rows);
    }

    private static long count(List<Document> docs, StatutIndexation state) {
        return docs.stream().filter(d -> d.getStatutIndexation() == state).count();
    }

    // ---------------------------------------------------------- the external engine

    private RagLabIndex raglab() {
        AgentEndpointService.EndpointView endpoint = endpoints.current();
        Optional<AgentAdminAudit> declaration = audit
                .findFirstByActionOrderByCreatedAtDescIdDesc("CORPUS_REBUILD_RAGLAB_DECLARED");
        String declaredBy = declaration.map(AgentAdminAudit::getActor).orElse(null);
        String declaredAt = declaration.map(a -> a.getCreatedAt() == null ? null
                : a.getCreatedAt().toString()).orElse(null);
        String declaredReason = declaration.map(AgentAdminAudit::getReason).orElse(null);

        if (!endpoint.configured()) {
            return new RagLabIndex(false, false, "Aucune adresse configurée : index RAGLab inconnu.",
                    null, null, null, null, null, null, declaration.isPresent(),
                    declaredBy, declaredAt, declaredReason, Map.of());
        }

        RagLabClient.Health probe = raglab.health();
        if (!probe.reachable()) {
            return new RagLabIndex(true, false,
                    "Service injoignable : l'état de son index ne peut pas être lu.",
                    null, null, null, null, null, null, declaration.isPresent(),
                    declaredBy, declaredAt, declaredReason, Map.of());
        }

        JsonNode payload = probe.payload();
        return new RagLabIndex(true, true,
                "État rapporté par le service lui-même (reconstruction hors console).",
                endpoint.url(),
                RagLabFacts.fact(payload, "documents", "docs", "file_count", "files", "nb_documents"),
                RagLabFacts.fact(payload, "chunks", "chunk_count", "nb_chunks", "segments"),
                RagLabFacts.fact(payload, "embeddings", "vectors", "embedding_count", "nb_embeddings"),
                RagLabFacts.text(payload, "model", "embedding_model", "modele"),
                RagLabFacts.text(payload, "updated_at", "last_indexed", "indexed_at", "last_ingest",
                        "derniere_indexation"),
                declaration.isPresent(), declaredBy, declaredAt, declaredReason,
                payload == null || !payload.isObject() ? Map.of() : JSON.convertValue(payload, RAW));
    }

}
