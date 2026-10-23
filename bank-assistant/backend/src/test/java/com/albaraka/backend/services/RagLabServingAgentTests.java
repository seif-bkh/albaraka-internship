package com.albaraka.backend.services;

import com.albaraka.backend.services.AgentWire;
import com.albaraka.backend.services.AgentWire.AgentReply;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a RAGLab answer becomes a turn the edge can serve: what the customer reads,
 * what the monitoring page replays, and how the two capability gaps (no stream,
 * no pipeline trace) are represented.
 */
class RagLabServingAgentTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode payload(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void answeredTurnCarriesTheTextTheSourcesAndTheClaims() {
        AgentReply reply = RagLabServingAgent.toReply(payload("""
                {"status": "answered", "answer": "Les frais de tenue de compte sont de 12 DT par an.",
                 "claims": [{"text": "12 DT par an.", "evidence": [{"source_id": "S1", "quote": "12 DT par an"}]}],
                 "sources": [{"source_id": "S1", "document": "Guide.docx", "chunk_id": "c17", "heading": "Frais"},
                             {"source_id": "S2", "document": "Grille.pdf", "chunk_id": "c02"}],
                 "seconds": 3.2, "cached": false, "inference_performed": true}
                """), "quels sont les frais ?");

        assertEquals("Les frais de tenue de compte sont de 12 DT par an.", reply.text());
        assertTrue(reply.grounded());
        assertTrue(reply.ragUsed());
        assertEquals("RAGLAB_ANSWERED", reply.intention());
        assertEquals("RAGLAB", reply.domain());
        assertNull(reply.topic(), "RAGLab has no bank-domain topic");
        assertEquals(java.util.List.of("Guide.docx", "Grille.pdf"), reply.sources(),
                "the stored sources are the documents visited, never the service's S1/S2 aliases");
        assertEquals(java.util.List.of("Guide.docx — Frais", "Grille.pdf"), reply.sourceTitles());
        assertTrue(reply.estimatedTokens() > 0);

        JsonNode trace = parse(reply.traceJson());
        assertEquals("RAGLAB", trace.get("engine").asText());
        assertEquals("answered", trace.get("status").asText());
        assertEquals("S1", trace.get("claims").get(0).get("evidence").get(0).get("source_id").asText());
        assertEquals("12 DT par an", trace.get("claims").get(0).get("evidence").get(0).get("quote").asText());
        assertEquals("S2", trace.get("uncited_source_ids").get(0).asText(),
                "a retrieved source no claim used is exactly what an operator audits");
        assertEquals(3.2, trace.get("seconds").asDouble(), 0.001);
    }

    @Test
    void refusalIsNotGroundedAndKeepsItsMachineReason() {
        AgentReply reply = RagLabServingAgent.toReply(payload("""
                {"status": "refused", "reason": "insufficient-evidence",
                 "answer": "Je ne trouve pas cette information dans les documents.", "claims": [], "sources": []}
                """), "une question hors corpus");

        assertFalse(reply.grounded());
        assertFalse(reply.ragUsed());
        assertEquals("RAGLAB_REFUSED_INSUFFICIENT_EVIDENCE", reply.intention());
        assertEquals("Je ne trouve pas cette information dans les documents.", reply.text());

        JsonNode trace = parse(reply.traceJson());
        assertEquals("insufficient-evidence", trace.get("reason").asText());
        assertTrue(trace.get("claims").isEmpty());
    }

    @Test
    void greetingAnswersWithoutClaimingItLookedAnythingUp() {
        AgentReply reply = RagLabServingAgent.toReply(payload("""
                {"status": "greeting", "answer": "Bonjour ! Comment puis-je vous aider ?",
                 "inference_performed": false}
                """), "bonjour");

        assertTrue(reply.grounded(), "a greeting is a normal answer");
        assertFalse(reply.ragUsed(), "nothing was retrieved for a greeting");
        assertEquals("RAGLAB_GREETING", reply.intention());
    }

    @Test
    void anUnknownStatusIsNeverGrounded() {
        // A status we do not recognise must fail closed: better a refusal than a
        // half-understood answer presented as grounded.
        AgentReply reply = RagLabServingAgent.toReply(payload("""
                {"status": "partial", "answer": "peut-être"}
                """), "?");

        assertFalse(reply.grounded());
        assertEquals("RAGLAB_PARTIAL", reply.intention());
    }

    @Test
    void claimsStandInWhenTheServiceSendsNoProse() {
        AgentReply reply = RagLabServingAgent.toReply(payload("""
                {"status": "answered",
                 "claims": [{"text": "Première affirmation.", "evidence": []},
                            {"text": "Seconde affirmation.", "evidence": []}],
                 "sources": []}
                """), "?");

        assertEquals("Première affirmation. Seconde affirmation.", reply.text());
    }

    @Test
    void anEmptyAnswerFallsBackToAnHonestRefusal() {
        AgentReply reply = RagLabServingAgent.toReply(payload("{}"), "?");
        AgentReply nothing = RagLabServingAgent.toReply(null, "?");

        assertFalse(reply.grounded());
        assertFalse(reply.text().isBlank(), "the customer must never get an empty bubble");
        assertEquals("RAGLAB_UNKNOWN", reply.intention());
        assertFalse(nothing.grounded());
    }

    @Test
    void onlyTheThreeSupportedLanguagesAreForwarded() {
        assertEquals("fr", RagLabServingAgent.queryLang("fr"));
        assertEquals("ar", RagLabServingAgent.queryLang("AR"));
        assertEquals("en", RagLabServingAgent.queryLang(" en "));
        assertNull(RagLabServingAgent.queryLang("auto"), "let the service decide");
        assertNull(RagLabServingAgent.queryLang(null));
        assertNull(RagLabServingAgent.queryLang("de"));
    }

    @Test
    void theStreamedStructuredBlockCarriesWhatTheBrowserShows() {
        AgentReply reply = RagLabServingAgent.toReply(payload("""
                {"status": "answered", "answer": "ok",
                 "sources": [{"source_id": "S1", "document": "Guide.docx", "heading": "Frais"}]}
                """), "?");

        Map<String, Object> structured = RagLabServingAgent.structured(reply);
        assertEquals("RAGLAB", structured.get("domain"));
        assertEquals("RAGLAB_ANSWERED", structured.get("intent"));
        assertEquals(java.util.List.of("Guide.docx — Frais"), structured.get("citation_titles"));

        // Nothing to cite: the key is absent rather than an empty list, so the UI
        // keeps its own fallback titles.
        AgentReply bare = RagLabServingAgent.toReply(payload("{\"status\": \"greeting\", \"answer\": \"hi\"}"), "?");
        assertFalse(RagLabServingAgent.structured(bare).containsKey("citation_titles"));
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
