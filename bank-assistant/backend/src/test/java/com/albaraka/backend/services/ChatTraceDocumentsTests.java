package com.albaraka.backend.services;

import com.albaraka.backend.services.ChatTraceDocuments.DocumentRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reviewer's question is "which documents did it read", and the answer must
 * come from the trace the pipeline stored, not from what the customer was shown.
 * A chunk retrieval dropped is still a document that was visited — that is exactly
 * the difference between "no source found" and "a weak source was found".
 */
class ChatTraceDocumentsTests {

    @Test
    void inHouseTraceListsEveryRetrievedChunkAndMarksTheDroppedOnes() {
        String trace = """
                {"steps":[
                  {"name":"retrieve","ms":12.0,"data":{"min_similarity":0.4,"candidates":[
                     {"source":"Guide_Interne.docx","title":"Guide interne des operations","similarity":0.81,"kept":true},
                     {"source":"Loi_2016-48.pdf","title":"Loi 2016-48","similarity":0.22,"kept":false}
                  ]}}
                ]}""";
        List<DocumentRef> docs = ChatTraceDocuments.fromTrace(trace, List.of("Guide_Interne.docx"), "commercial");

        assertEquals(2, docs.size(), "the dropped chunk counts as visited");
        assertEquals("Guide_Interne.docx", docs.get(0).document());
        assertEquals(Boolean.TRUE, docs.get(0).kept());
        assertTrue(docs.get(0).cited(), "the answer's own source is flagged");
        assertFalse(docs.get(1).kept());
        assertFalse(docs.get(1).cited());
        assertEquals(0.81, docs.get(0).similarity().doubleValue(), 0.0001);
    }

    @Test
    void ragLabTraceListsDocumentsAndSectionsNotAliasIds() {
        String trace = """
                {"engine":"RAGLAB","status":"answered","sources":[
                   {"source_id":"S1","document":"Guide.docx","chunk_id":"c17","heading":"Frais"},
                   {"source_id":"S2","document":"Grille.pdf","chunk_id":"c02"}
                ]}""";
        List<DocumentRef> docs = ChatTraceDocuments.fromTrace(trace, List.of("Guide.docx"), "RAGLAB_ANSWERED");

        assertEquals(2, docs.size());
        assertEquals("Guide.docx", docs.get(0).document());
        assertEquals("Frais", docs.get(0).heading());
        assertEquals("c17", docs.get(0).chunkId());
        assertTrue(docs.get(0).cited());
        assertEquals("Grille.pdf", docs.get(1).document());
        assertEquals("Guide.docx › Frais", docs.get(0).label());
    }

    @Test
    void storedSourcesStandInWhenThereIsNoTrace() {
        List<DocumentRef> docs = ChatTraceDocuments.fromTrace(null, List.of("faq_3", "Guide.docx"), "commercial");
        assertEquals(2, docs.size());
        assertEquals("faq_3", docs.get(0).document());
        assertTrue(docs.stream().allMatch(DocumentRef::cited));
    }

    @Test
    void unreadableTraceDegradesToTheStoredSources() {
        List<DocumentRef> docs = ChatTraceDocuments.fromTrace("{not json", List.of("Loi.pdf"), "RAGLAB_ANSWERED");
        assertEquals(List.of("Loi.pdf"), docs.stream().map(DocumentRef::document).toList());
    }

    @Test
    void modelIsTakenFromThePipelineTraceOnlyWhenItNamedOne() {
        String trace = """
                {"steps":[{"name":"llm_prompt","ms":3.0,"data":{"model":"llama-3.3-70b-versatile","system":"…"}}]}""";
        assertEquals("llama-3.3-70b-versatile", ChatTraceDocuments.modelFromTrace(trace));
        assertNull(ChatTraceDocuments.modelFromTrace("{\"steps\":[{\"name\":\"gate\",\"data\":{}}]}"),
                "no model in the trace means the engine never said which one it used");
        assertNull(ChatTraceDocuments.modelFromTrace(null));
    }
}
