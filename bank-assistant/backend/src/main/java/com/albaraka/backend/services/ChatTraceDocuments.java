package com.albaraka.backend.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The documents a stored exchange actually consulted, rebuilt from the agent trace
 * that was persisted with it.
 *
 * <p>{@code messages.sources_utilisees} answers "which documents did the answer
 * lean on". An operator auditing a wrong answer needs the wider picture: which
 * chunks retrieval pulled, which of them the relevance threshold dropped, and
 * which heading inside each document was read. That lives in the trace JSON, and
 * this class is the single reader of it — so the admin console shows documents
 * visited, not the short citation labels a customer's answer may have carried.
 *
 * <p>Two shapes, one output: the in-house pipeline records a {@code retrieve} step
 * per pass ({@code candidates[]}); RAGLab records {@code sources[]} plus the
 * verbatim quote each claim used. A turn with an unreadable or absent trace
 * degrades to the stored sources as document names — better a coarse list than an
 * empty one.
 *
 * <p>The same trace also carries the model that wrote the answer (the in-house
 * pipeline names it on its {@code llm_prompt} step), which the console shows next to
 * the token counts; {@link #modelFromTrace(String)} is that lookup.
 *
 * <p>Pure and static on purpose: parse-only, no database, unit-testable.
 */
public final class ChatTraceDocuments {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    /** Cap on what one row shows; a retrieval pass rarely exceeds it and the drawer must stay readable. */
    private static final int MAX_DOCUMENTS = 24;

    private ChatTraceDocuments() {
    }

    /**
     * One document (or chunk of one) the pipeline looked at.
     *
     * @param document   stable identifier — the ingestion key, e.g. {@code Guide_Interne.docx}
     * @param title      human-readable name, when the engine supplied one
     * @param heading    section / heading inside the document
     * @param chunkId    the retrieval unit, when the engine identifies one
     * @param similarity how close the chunk was to the question (in-house engine)
     * @param kept       false when the relevance threshold dropped it — retrieved but not used
     * @param cited      true when the answer's own sources name this document
     * @param engine     {@code LEGACY} or {@code RAGLAB}, so the console can say where it came from
     */
    public record DocumentRef(String document, String title, String heading, String chunkId,
                              Double similarity, Boolean kept, boolean cited, String engine) {

        /** What an operator scans first: the document, narrowed to its section when known. */
        public String label() {
            String name = document != null && !document.isBlank() ? document : title;
            if (name == null || name.isBlank()) {
                name = "document inconnu";
            }
            return heading == null || heading.isBlank() ? name : name + " › " + heading;
        }
    }

    /**
     * @param traceJson  the {@code messages.trace_json} column, possibly null
     * @param sources    the {@code messages.sources_utilisees} column (cited documents)
     * @param intention  used only to recognise a RAGLab turn, whose trace has no steps
     */
    public static List<DocumentRef> fromTrace(String traceJson, List<String> sources, String intention) {
        List<DocumentRef> out = new ArrayList<>();
        Map<String, Object> trace = parse(traceJson);
        String engine = MonitoringKpiService.engineOf(intention);
        if (!trace.isEmpty()) {
            readPipelineSteps(trace, cited(sources), engine, out);
            readServiceSources(trace, cited(sources), engine, out);
        }
        if (out.isEmpty()) {
            // No trace to widen with (older rows, or a turn persisted before tracing):
            // the stored sources are still the truth about what the answer used.
            for (String source : nullSafe(sources)) {
                if (source != null && !source.isBlank()) {
                    out.add(new DocumentRef(source.trim(), null, null, null, null, null, true, engine));
                }
            }
        }
        return out.size() > MAX_DOCUMENTS ? out.subList(0, MAX_DOCUMENTS) : out;
    }

    /**
     * The model that wrote the answer, when the engine named one — the pipeline
     * records it on its {@code llm_prompt} step. Null means the engine does not
     * expose it (RAGLab answers without saying which model it used), and the caller
     * must present the engine, not a guess at a model.
     */
    public static String modelFromTrace(String traceJson) {
        Map<String, Object> trace = parse(traceJson);
        String direct = string(trace.get("model"));
        if (direct != null) {
            return direct;
        }
        for (Map<String, Object> step : mapList(trace.get("steps"))) {
            if (!"llm_prompt".equals(step.get("name"))) {
                continue;
            }
            if (step.get("data") instanceof Map<?, ?> data) {
                String model = string(castMap(data).get("model"));
                if (model != null) {
                    return model;
                }
            }
        }
        return null;
    }

    /** The in-house pipeline: every {@code retrieve} step, kept or dropped by the threshold. */
    private static void readPipelineSteps(Map<String, Object> trace, List<String> cited,
                                          String engine, List<DocumentRef> out) {
        for (Map<String, Object> step : mapList(trace.get("steps"))) {
            if (!"retrieve".equals(step.get("name"))) {
                continue;
            }
            for (Map<String, Object> candidate : mapList(step.get("data") instanceof Map<?, ?> data
                    ? data.get("candidates") : null)) {
                String title = string(candidate.get("title"));
                String source = string(candidate.get("source"));
                out.add(new DocumentRef(source != null ? source : title, title, null, null,
                        number(candidate.get("similarity")), bool(candidate.get("kept")),
                        isCited(source != null ? source : title, cited), engine));
            }
        }
    }

    /** RAGLab: the sources the service returned, with the claims' quotes marking what was used. */
    private static void readServiceSources(Map<String, Object> trace, List<String> cited,
                                           String engine, List<DocumentRef> out) {
        for (Map<String, Object> source : mapList(trace.get("sources"))) {
            String document = string(source.get("document"));
            String sourceId = string(source.get("source_id"));
            out.add(new DocumentRef(document != null ? document : sourceId, document,
                    string(source.get("heading")), string(source.get("chunk_id")),
                    null, null, isCited(document, cited), engine));
        }
    }

    private static List<String> cited(List<String> sources) {
        return sources == null ? List.of() : sources;
    }

    private static boolean isCited(String document, List<String> cited) {
        if (document == null || document.isBlank()) {
            return false;
        }
        return cited.stream().filter(s -> s != null).anyMatch(s -> s.trim().equals(document.trim())
                || s.trim().startsWith(document.trim() + " — "));
    }

    private static Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = JSON.readValue(json, MAP);
            return parsed instanceof Map<?, ?> map ? castMap(map) : Map.of();
        } catch (Exception e) {
            // A corrupt trace is a display gap, not a reason to fail the whole feed.
            return Map.of("error", "unreadable trace");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    private static List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    out.add(castMap(map));
                }
            }
        }
        return out;
    }

    private static List<String> nullSafe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private static Boolean bool(Object value) {
        return value instanceof Boolean b ? b : null;
    }
}
