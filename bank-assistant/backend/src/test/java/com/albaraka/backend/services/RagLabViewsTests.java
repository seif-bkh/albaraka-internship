package com.albaraka.backend.services;

import com.albaraka.backend.services.RagLabViews.AnswerView;
import com.albaraka.backend.services.RagLabViews.ChunksView;
import com.albaraka.backend.services.RagLabViews.SearchView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mapping from the service's payloads to what the console renders.
 *
 * <p>Payload-driven on purpose (no HTTP, no mock server): these are the shapes
 * the screens trust, including the ones a new service version might send.
 */
class RagLabViewsTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode payload(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String ANSWERED = """
            {
              "status": "answered",
              "answer": "Les frais de tenue de compte sont de 12 DT par an.",
              "claims": [
                {"text": "Les frais de tenue de compte sont de 12 DT par an.",
                 "evidence": [{"source_id": "S1", "quote": "frais de tenue de compte : 12 DT par an"}]},
                {"text": "Ils sont prélevés chaque trimestre.",
                 "evidence": [{"source_id": "S1", "quote": "prélèvement trimestriel"}]}
              ],
              "sources": [
                {"source_id": "S1", "document": "Guide_Interne.docx", "chunk_id": "c17", "heading": "Frais"},
                {"source_id": "S2", "document": "Grille_tarifaire.pdf", "chunk_id": "c02", "heading": "Comptes"}
              ],
              "cached": false, "inference_performed": true, "seconds": 4.8
            }
            """;

    @Test
    void answeredPayloadKeepsClaimsQuotesAndSourcesInOrder() {
        AnswerView view = RagLabViews.answer(payload(ANSWERED));

        assertEquals("answered", view.status());
        assertTrue(view.answered());
        assertFalse(view.refused());
        assertEquals(2, view.claimCount());
        assertEquals(2, view.citedClaims());
        assertEquals(0, view.uncitedClaims());
        assertEquals("S1", view.claims().get(0).evidence().get(0).sourceId());
        assertEquals("frais de tenue de compte : 12 DT par an",
                view.claims().get(0).evidence().get(0).quote());
        assertEquals(2, view.sources().size());
        assertEquals("Guide_Interne.docx", view.sources().get(0).document());
        assertEquals("c17", view.sources().get(0).chunkId());
        assertEquals(Boolean.FALSE, view.cached());
        assertEquals(Boolean.TRUE, view.inferencePerformed());
        assertEquals(4.8, view.seconds(), 0.001);
        // S2 was retrieved but no claim uses it: the screen must say so, because a
        // source nobody cites is exactly what an operator is looking for when a
        // plausible answer feels thin.
        assertEquals(java.util.List.of("S2"), view.uncitedSourceIds());
    }

    @Test
    void aClaimWithoutAQuoteAndASourceNobodyCitedAreBothVisible() {
        // The two things an operator must be able to see: prose that no gate could
        // check, and a retrieved source that no claim uses.
        AnswerView view = RagLabViews.answer(payload("""
                {
                  "status": "answered",
                  "claims": [
                    {"text": "sourcée", "evidence": [{"source_id": "S1", "quote": "extrait"}]},
                    {"text": "prose libre", "evidence": []}
                  ],
                  "sources": [
                    {"source_id": "S1", "document": "a.docx", "chunk_id": "c1", "heading": "H"},
                    {"source_id": "S2", "document": "b.docx", "chunk_id": "c2", "heading": "H"}
                  ]
                }
                """));

        assertEquals(2, view.claimCount());
        assertEquals(1, view.citedClaims());
        assertEquals(1, view.uncitedClaims());
        assertFalse(view.claims().get(1).cited());
        assertEquals(java.util.List.of("S2"), view.uncitedSourceIds());
    }

    @Test
    void refusedPayloadCarriesTheMachineReason() {
        AnswerView view = RagLabViews.answer(payload("""
                {"status": "refused", "reason": "insufficient_evidence",
                 "answer": "Je ne trouve pas cette information.", "claims": [], "sources": []}
                """));

        assertTrue(view.refused());
        assertFalse(view.answered());
        assertEquals("insufficient_evidence", view.reason());
        assertEquals("Je ne trouve pas cette information.", view.answerText());
        assertEquals(0, view.claimCount());
        assertTrue(view.sources().isEmpty());
    }

    @Test
    void greetingIsAStatusAndNothingElse() {
        AnswerView view = RagLabViews.answer(payload("""
                {"status": "greeting", "answer": "Bonjour !", "inference_performed": false}
                """));

        assertTrue(view.greeting());
        assertEquals(Boolean.FALSE, view.inferencePerformed());
        assertEquals(0, view.claimCount());
        assertTrue(view.sources().isEmpty());
    }

    @Test
    void anUnknownShapeIsToleratedNeverFatal() {
        // A different service version must not blank the screen: absent arrays and
        // absent fields simply render as empty, and the raw payload is still shown.
        AnswerView view = RagLabViews.answer(payload("{\"status\":\"answered\",\"unexpected\":{\"a\":1}}"));

        assertEquals("answered", view.status());
        assertNull(view.answerText());
        assertEquals(0, view.claimCount());
        assertTrue(view.sources().isEmpty());
        assertTrue(view.uncitedSourceIds().isEmpty());
        assertNull(view.cached());

        assertNull(RagLabViews.answer(null));
    }

    @Test
    void searchPayloadsRankHitsAndAcceptAlternativeFieldNames() {
        SearchView view = RagLabViews.search(payload("""
                {"question": "frais de tenue de compte",
                 "hits": [
                   {"source_id": "S1", "document": "Guide.docx", "chunk_id": "c17", "heading": "Frais",
                    "score": 0.71, "excerpt": "frais de tenue de compte : 12 DT"},
                   {"id": "S3", "document_name": "Autre.docx", "chunkId": "c04",
                    "similarity": 0.38, "text": "tenue de compte"}
                 ]}
                """));

        assertEquals("frais de tenue de compte", view.question());
        assertEquals(2, view.hits().size());
        assertEquals("S1", view.hits().get(0).sourceId());
        assertEquals(0.71, view.hits().get(0).score(), 0.001);
        assertEquals("S3", view.hits().get(1).sourceId(), "id is accepted as a source id");
        assertEquals("c04", view.hits().get(1).chunkId());
        assertEquals(0.38, view.hits().get(1).score(), 0.001);

        assertTrue(RagLabViews.search(payload("{\"results\":[]}")).hits().isEmpty());
    }

    @Test
    void chunkLookupReportsWhatWasFoundAndWhere() {
        ChunksView found = RagLabViews.chunks(payload("""
                {"text": "12 DT par an", "total": 2,
                 "matches": [
                   {"document": "Guide.docx", "chunk_id": "c17", "heading": "Frais",
                    "text": "frais de tenue de compte : 12 DT par an", "offset": 41, "position": 1},
                   {"document": "Note.pdf", "chunk_id": "c90", "text": "12 DT par an", "position": 2}
                 ]}
                """));

        assertEquals("12 DT par an", found.query());
        assertEquals(2, found.total());
        assertEquals(2, found.matches().size());
        assertEquals("Guide.docx", found.matches().get(0).document());
        assertEquals(41, found.matches().get(0).offset());
        assertEquals(2, found.matches().get(1).position());
        assertNull(found.matches().get(1).heading());

        ChunksView none = RagLabViews.chunks(payload("{\"matches\":[]}"));
        assertTrue(none.matches().isEmpty());
        assertNull(none.total());
    }
}
