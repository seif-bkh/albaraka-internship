package com.albaraka.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The one risky habit of both screens: reading a fact out of a service whose
 * vocabulary we only partly know. The rule is that an unknown shape yields null —
 * "not communicated" — and never a zero, which would read as an empty index.
 */
class RagLabFactsTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode json(String raw) {
        try {
            return JSON.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void countsAreFoundUnderTheNamesTheServiceMightUse() {
        assertEquals(12L, RagLabFacts.fact(json("{\"documents\": 12}"), "documents", "docs"));
        assertEquals(12L, RagLabFacts.fact(json("{\"docs\": 12}"), "documents", "docs"));
        assertEquals(12L, RagLabFacts.fact(json("{\"file_count\": 12}"), "documents", "file_count"));
        // Numbered as a string, as a CLI-flavoured status often does.
        assertEquals(12L, RagLabFacts.fact(json("{\"chunks\": \"12\"}"), "chunks"));
        // A list of segments answers "how many" without a count field.
        assertEquals(3L, RagLabFacts.fact(json("{\"chunks\": [{}, {}, {}]}"), "chunks"));
    }

    @Test
    void countsAreAlsoFoundInsideTheObjectsAStatusReportNestsThem() {
        assertEquals(40L, RagLabFacts.fact(json("{\"index\": {\"chunks\": 40}}"), "chunks"));
        assertEquals(40L, RagLabFacts.fact(json("{\"stats\": {\"chunks\": 40}}"), "chunks"));
        assertEquals(40L, RagLabFacts.fact(json("{\"status\": {\"chunks\": 40}}"), "chunks"));
        // Top level wins over a nested copy, so the most direct answer is used.
        assertEquals(7L, RagLabFacts.fact(json("{\"chunks\": 7, \"index\": {\"chunks\": 40}}"), "chunks"));
    }

    @Test
    void anUnknownShapeIsNullRatherThanZero() {
        assertNull(RagLabFacts.fact(json("{\"message\": \"ok\"}"), "chunks"));
        assertNull(RagLabFacts.fact(json("{\"chunks\": null}"), "chunks"));
        assertNull(RagLabFacts.fact(json("{\"chunks\": \"beaucoup\"}"), "chunks"));
        assertNull(RagLabFacts.fact(json("{}"), "chunks"));
        assertNull(RagLabFacts.fact(null, "chunks"));
        // Malformed input never propagates: this is monitoring, not a failure path.
        assertNull(RagLabFacts.fact(json("[]"), "chunks"));
        assertNull(RagLabFacts.fact(json("\"up\""), "chunks"));
    }

    @Test
    void textFactsAreReadTheSameWay() {
        assertEquals("bge-m3", RagLabFacts.text(json("{\"model\": \"bge-m3\"}"), "model"));
        assertEquals("2026-09-15T08:00:00",
                RagLabFacts.text(json("{\"index\": {\"updated_at\": \"2026-09-15T08:00:00\"}}"),
                        "updated_at", "last_indexed"));
        assertNull(RagLabFacts.text(json("{\"model\": null}"), "model"));
        assertNull(RagLabFacts.text(json("{\"other\": \"x\"}"), "model"));
    }
}
