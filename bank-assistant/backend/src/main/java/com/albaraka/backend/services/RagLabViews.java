package com.albaraka.backend.services;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Presentation mapping for the RAG service's payloads.
 *
 * <p>The console shows two things side by side: this typed view (claims, verbatim
 * quotes, sources, ranking) and the raw payload, so an operator can still read a
 * shape we did not anticipate. Every getter is tolerant on purpose — a missing
 * field becomes null or an empty list, never an exception, because a new service
 * version must not blank the screen.
 *
 * <p>Kept static and free of Spring so it can be tested against stored payloads.
 */
public final class RagLabViews {

    private RagLabViews() {
    }

    // ------------------------------------------------------------------ answer

    public record EvidenceView(String sourceId, String quote) {
    }

    public record ClaimView(String text, List<EvidenceView> evidence) {
        /** A claim with no verbatim quote behind it is model prose, not evidence. */
        public boolean cited() {
            return evidence != null && !evidence.isEmpty();
        }
    }

    public record SourceView(String sourceId, String document, String chunkId, String heading) {
    }

    /**
     * One grounded answer. {@code status} is the service's own vocabulary
     * ({@code answered} / {@code refused} / {@code greeting}), {@code reason} the
     * machine code behind a refusal.
     */
    public record AnswerView(String status, String reason, String answerText, List<ClaimView> claims,
                             List<SourceView> sources, Boolean cached, Boolean inferencePerformed,
                             Double seconds, int citedClaims, int uncitedClaims,
                             List<String> uncitedSourceIds) {

        public boolean answered() {
            return "answered".equalsIgnoreCase(status);
        }

        public boolean refused() {
            return "refused".equalsIgnoreCase(status);
        }

        public boolean greeting() {
            return "greeting".equalsIgnoreCase(status);
        }

        /** How many claims carry a quote the gate could have checked. */
        public int claimCount() {
            return claims == null ? 0 : claims.size();
        }
    }

    public static AnswerView answer(JsonNode payload) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return null;
        }
        List<ClaimView> claims = new ArrayList<>();
        Set<String> cited = new LinkedHashSet<>();
        for (JsonNode claim : array(payload, "claims")) {
            List<EvidenceView> evidence = new ArrayList<>();
            for (JsonNode item : array(claim, "evidence")) {
                String sourceId = text(item, "source_id", "sourceId", "source");
                if (sourceId != null) {
                    cited.add(sourceId);
                }
                evidence.add(new EvidenceView(sourceId, text(item, "quote", "excerpt")));
            }
            claims.add(new ClaimView(text(claim, "text", "claim"), evidence));
        }

        List<SourceView> sources = new ArrayList<>();
        for (JsonNode source : array(payload, "sources")) {
            sources.add(new SourceView(
                    text(source, "source_id", "sourceId", "id"),
                    text(source, "document", "document_name", "source"),
                    text(source, "chunk_id", "chunkId"),
                    text(source, "heading", "section", "title")));
        }

        List<String> uncited = new ArrayList<>();
        for (SourceView source : sources) {
            if (source.sourceId() != null && !cited.contains(source.sourceId())) {
                uncited.add(source.sourceId());
            }
        }

        int citedClaims = 0;
        for (ClaimView claim : claims) {
            if (claim.cited()) {
                citedClaims++;
            }
        }

        return new AnswerView(
                text(payload, "status"),
                text(payload, "reason", "refusal_reason"),
                text(payload, "answer", "text", "summary"),
                claims,
                sources,
                bool(payload, "cached"),
                bool(payload, "inference_performed", "inferencePerformed"),
                number(payload, "seconds", "duration_seconds", "latency_s"),
                citedClaims,
                claims.size() - citedClaims,
                uncited);
    }

    // -------------------------------------------------------------- retrieval

    public record HitView(String sourceId, String document, String chunkId, String heading,
                          Double score, String excerpt) {
    }

    public record SearchView(String question, List<HitView> hits, String reason) {
    }

    public static SearchView search(JsonNode payload) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return null;
        }
        List<HitView> hits = new ArrayList<>();
        for (JsonNode hit : firstArray(payload, "hits", "results", "chunks", "matches")) {
            hits.add(new HitView(
                    text(hit, "source_id", "sourceId", "id"),
                    text(hit, "document", "document_name", "source"),
                    text(hit, "chunk_id", "chunkId"),
                    text(hit, "heading", "section", "title"),
                    number(hit, "score", "similarity", "distance"),
                    text(hit, "excerpt", "text", "content")));
        }
        return new SearchView(text(payload, "question", "query"), hits, text(payload, "reason"));
    }

    // ----------------------------------------------------------- chunk lookup

    public record ChunkMatchView(String document, String chunkId, String heading, String text,
                                 Integer offset, Integer position) {
    }

    public record ChunksView(String query, List<ChunkMatchView> matches, Integer total) {
    }

    public static ChunksView chunks(JsonNode payload) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return null;
        }
        List<ChunkMatchView> matches = new ArrayList<>();
        for (JsonNode match : firstArray(payload, "matches", "hits", "results", "chunks")) {
            matches.add(new ChunkMatchView(
                    text(match, "document", "document_name", "source"),
                    text(match, "chunk_id", "chunkId", "id"),
                    text(match, "heading", "section", "title"),
                    text(match, "text", "content", "excerpt"),
                    integer(match, "offset", "start"),
                    integer(match, "position", "index", "rank")));
        }
        return new ChunksView(text(payload, "text", "query"), matches, integer(payload, "total", "count"));
    }

    // ------------------------------------------------------------------ helpers

    private static Iterable<JsonNode> array(JsonNode parent, String field) {
        return firstArray(parent, field);
    }

    /** First field among {@code names} that is an array; empty when none is. */
    private static List<JsonNode> firstArray(JsonNode parent, String... names) {
        List<JsonNode> out = new ArrayList<>();
        if (parent == null) {
            return out;
        }
        for (String name : names) {
            JsonNode node = parent.get(name);
            if (node != null && node.isArray()) {
                node.forEach(out::add);
                return out;
            }
        }
        return out;
    }

    /** First non-blank text among {@code names}. */
    private static String text(JsonNode parent, String... names) {
        if (parent == null) {
            return null;
        }
        for (String name : names) {
            JsonNode node = parent.get(name);
            if (node != null && node.isValueNode() && !node.asText("").isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    private static Boolean bool(JsonNode parent, String... names) {
        if (parent == null) {
            return null;
        }
        for (String name : names) {
            JsonNode node = parent.get(name);
            if (node != null && node.isBoolean()) {
                return node.asBoolean();
            }
        }
        return null;
    }

    private static Double number(JsonNode parent, String... names) {
        if (parent == null) {
            return null;
        }
        for (String name : names) {
            JsonNode node = parent.get(name);
            if (node != null && node.isNumber()) {
                return node.asDouble();
            }
        }
        return null;
    }

    private static Integer integer(JsonNode parent, String... names) {
        if (parent == null) {
            return null;
        }
        for (String name : names) {
            JsonNode node = parent.get(name);
            if (node != null && node.isNumber()) {
                return node.asInt();
            }
        }
        return null;
    }
}
