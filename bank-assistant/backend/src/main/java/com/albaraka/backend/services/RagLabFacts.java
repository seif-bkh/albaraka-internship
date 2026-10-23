package com.albaraka.backend.services;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Reading facts out of a service that reports them in its own words.
 *
 * <p>Both the corpus screen and the configuration screen show things only RAGLab
 * can tell us (its index size, the models it loads), and we know neither the
 * vocabulary nor the nesting it uses. The rule is the same everywhere: try a
 * handful of plausible names at the top level and inside the containers a status
 * report usually nests them in, and when nothing matches return {@code null} —
 * "not communicated" — rather than a zero that would read as a real measurement.
 *
 * <p>The raw payload is always shown next to the interpreted values, so a
 * vocabulary we did not anticipate costs the reader a glance, not an incident.
 */
public final class RagLabFacts {

    private RagLabFacts() {
    }

/**
 * A number the service may report under any of these names, at the top level or
 * inside an {@code index} / {@code corpus} / {@code stats} object. Absent stays
 * null: an unknown index state is reported as unknown, never as zero.
 */
static Long fact(JsonNode payload, String... names) {
    JsonNode node = find(payload, names);
    if (node == null) {
        return null;
    }
    if (node.isNumber()) {
        return node.asLong();
    }
    if (node.isTextual()) {
        try {
            return Long.parseLong(node.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    if (node.isArray()) {
        return (long) node.size();
    }
    return null;
}

static String text(JsonNode payload, String... names) {
    JsonNode node = find(payload, names);
    return node != null && node.isValueNode() ? node.asText() : null;
}

private static JsonNode find(JsonNode payload, String... names) {
    if (payload == null) {
        return null;
    }
    for (String container : new String[] {null, "index", "corpus", "stats", "status"}) {
        JsonNode scope = container == null ? payload : payload.get(container);
        if (scope == null || !scope.isObject()) {
            continue;
        }
        for (String name : names) {
            JsonNode found = scope.get(name);
            if (found != null && !found.isNull()) {
                return found;
            }
        }
    }
    return null;
}
}
