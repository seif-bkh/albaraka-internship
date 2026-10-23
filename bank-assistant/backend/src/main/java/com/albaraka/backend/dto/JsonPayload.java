package com.albaraka.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Jackson 2's {@link JsonNode} never crosses the wire in raw form.
 *
 * <p>Spring Boot 4's MVC layer serializes responses with Jackson 3
 * ({@code tools.jackson}), which does not know how to serialize
 * {@code com.fasterxml} types — a DTO carrying a {@code JsonNode} field fails
 * with "Type definition error". Converting the node to plain values (maps,
 * lists, strings, numbers, booleans) at the DTO boundary keeps the JSON shape
 * on the wire identical, minus the Jackson 2 object identity.
 */
public final class JsonPayload {

    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonPayload() {
    }

    /** The node as plain values, or {@code null} for an absent or JSON-null node. */
    public static Object toPlain(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return JSON.convertValue(node, Object.class);
    }
}
