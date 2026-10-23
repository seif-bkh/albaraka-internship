package com.albaraka.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The only way this backend talks to the external RAG service.
 *
 * <p>Read paths used by the admin console: the health probe (slice 0), the
 * grounded answer, the retrieval query and the "does this quote exist in the
 * corpus?" lookup (slice 1). Configuration and ingestion calls land here too
 * when their screens do.
 *
 * <p>Timeouts differ per call class: a probe or a lookup must fail fast, while a
 * grounded answer waits on a chat model and gets a minute. Evaluations and
 * harness50 get up to 16 minutes.
 */
@Service
public class RagLabClient {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final int SEARCH_READ_TIMEOUT_MS = 20_000;
    private static final int ANSWER_READ_TIMEOUT_MS = 90_000;
    private static final int LONG_READ_TIMEOUT_MS = 960_000;

    /** RAGLab authenticates every endpoint with this header once a token is deployed. */
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final AgentEndpointService endpoints;
    private final String serviceToken;
    private final RestTemplate restTemplate;
    private final RestTemplate searchTemplate;
    private final RestTemplate answerTemplate;
    private final RestTemplate longTemplate;
    private final ObjectMapper json = new ObjectMapper();

    public RagLabClient(AgentEndpointService endpoints,
                        @Value("${app.agent.raglab.token:}") String serviceToken) {
        this.endpoints = endpoints;
        this.serviceToken = serviceToken;
        this.restTemplate = template(READ_TIMEOUT_MS);
        this.searchTemplate = template(SEARCH_READ_TIMEOUT_MS);
        this.answerTemplate = template(ANSWER_READ_TIMEOUT_MS);
        this.longTemplate = template(LONG_READ_TIMEOUT_MS);
    }

    /** The address every call will use, or null when none is configured yet. */
    public String endpointUrl() {
        return endpoints.current().url();
    }

    private static RestTemplate template(int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    /** Outcome of a health probe: the failure is data, not an exception. */
    public record Health(boolean reachable, Integer httpStatus, long latencyMs,
                         JsonNode payload, String error, String errorReason) {

        public static Health up(JsonNode payload, int httpStatus, long latencyMs) {
            return new Health(true, httpStatus, latencyMs, payload, null, null);
        }

        public static Health down(long latencyMs, String error, String reason) {
            return new Health(false, null, latencyMs, null, error, reason);
        }
    }

    /** A successful call and how long it took (the console shows the latency). */
    public record Result(JsonNode payload, long latencyMs) {
    }

    /** {@code GET /health}. */
    public Health health() {
        AgentEndpointService.EndpointView endpoint = endpoints.current();
        if (!endpoint.configured()) {
            return Health.down(0, "Aucune adresse n'est configurée pour le service RAG.",
                    RagLabException.NOT_CONFIGURED);
        }
        long started = System.nanoTime();
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint.url() + "/health", HttpMethod.GET,
                    new HttpEntity<>(probeHeaders()), String.class);
            long ms = elapsedMs(started);
            if (!response.getStatusCode().is2xxSuccessful()) {
                return Health.down(ms,
                        "Le service a répondu " + response.getStatusCode().value() + ".",
                        RagLabException.BAD_RESPONSE);
            }
            return Health.up(parse(response.getBody()), response.getStatusCode().value(), ms);
        } catch (RestClientException | IllegalArgumentException e) {
            return Health.down(elapsedMs(started), unreachableMessage(e, endpoint.url()),
                    RagLabException.UNREACHABLE);
        }
    }

    /**
     * {@code POST /chunks/search} — "could this quote ever validate?". Substring
     * lookup in the corpus, no model call.
     */
    public Result searchChunks(String text, Integer limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        if (limit != null) {
            body.put("limit", limit);
        }
        return post("/chunks/search", body, restTemplate);
    }

    /** {@code POST /search} — ranked chunks for a question, no chat model. */
    public Result search(String question, Integer k, String mode, String langFilter,
                         Integer neighborRadius) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", question);
        putIfPresent(body, "k", k);
        putIfPresent(body, "mode", mode);
        putIfPresent(body, "lang_filter", langFilter);
        putIfPresent(body, "neighbor_radius", neighborRadius);
        return post("/search", body, searchTemplate);
    }

    /** {@code POST /answer} — one grounded answer: embed + chat + claim gate. */
    public Result answer(String question, Integer k, Boolean includeExcerpts, String queryLang,
                         String mode, String langFilter) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", question);
        putIfPresent(body, "k", k);
        putIfPresent(body, "include_excerpts", includeExcerpts);
        putIfPresent(body, "query_lang", queryLang);
        putIfPresent(body, "mode", mode);
        putIfPresent(body, "lang_filter", langFilter);
        return post("/answer", body, answerTemplate);
    }

    /** {@code GET /models} — provider and model registries. */
    public Result models() {
        return get("/models", restTemplate);
    }

    /** {@code GET /profile} — read current profile settings. */
    public Result profile() {
        return get("/profile", restTemplate);
    }

    /** {@code POST /profile} — update profile settings. */
    public Result updateProfile(Map<String, Object> body) {
        return post("/profile", body, restTemplate);
    }

    /** {@code GET /keys} — registered API keys and mask status. */
    public Result keys() {
        return get("/keys", restTemplate);
    }

    /** {@code POST /keys} — set an API key. */
    public Result setKey(Map<String, Object> body) {
        return post("/keys", body, restTemplate);
    }

    /** {@code DELETE /keys/{env}} — delete an API key. */
    public Result deleteKey(String env, Boolean persist) {
        String path = "/keys/" + env + (Boolean.TRUE.equals(persist) ? "?persist=true" : "?persist=false");
        return delete(path, restTemplate);
    }

    /** {@code GET /inspect} — chunking and corpus statistics. */
    public Result inspect(Integer limit) {
        String path = "/inspect" + (limit != null ? "?limit=" + limit : "");
        return get(path, restTemplate);
    }

    /** {@code POST /embeddings/sanity} — test embedding cross-lingual cosines. */
    public Result embeddingsSanity() {
        return post("/embeddings/sanity", new LinkedHashMap<>(), searchTemplate);
    }

    /** {@code POST /ingest} — start asynchronous background ingestion. */
    public Result ingest(Boolean reset) {
        String path = "/ingest" + (Boolean.TRUE.equals(reset) ? "?reset=true" : "?reset=false");
        return post(path, new LinkedHashMap<>(), restTemplate);
    }

    /** {@code GET /ingest/status} — status of current or last ingestion. */
    public Result ingestStatus() {
        return get("/ingest/status", restTemplate);
    }

    /** {@code POST /evaluate} — evaluate a question set. */
    public Result evaluate(Map<String, Object> body) {
        return post("/evaluate", body, longTemplate);
    }

    /** {@code POST /diagnostics/harness50} — offline 50-question harness. */
    public Result diagnosticsHarness50() {
        return post("/diagnostics/harness50", new LinkedHashMap<>(), longTemplate);
    }

    /** {@code POST /diagnostics/catalog} — live xKiro catalog query. */
    public Result diagnosticsCatalog() {
        return post("/diagnostics/catalog", new LinkedHashMap<>(), searchTemplate);
    }

    /** {@code GET /documents} — document feed status list. */
    public Result documents() {
        return get("/documents", restTemplate);
    }

    /** {@code GET /documents/{id}} — single document status. */
    public Result document(String id) {
        return get("/documents/" + id, restTemplate);
    }

    /** {@code POST /documents} — push a document to RAGLab feed. */
    public Result pushDocument(String id, Boolean index, Map<String, Object> body) {
        StringBuilder path = new StringBuilder("/documents?");
        if (id != null && !id.isBlank()) {
            path.append("id=").append(id.trim()).append("&");
        }
        path.append("index=").append(Boolean.TRUE.equals(index));
        return post(path.toString(), body, restTemplate);
    }

    /** {@code DELETE /documents/{id}} — remove file and purge its chunks immediately. */
    public Result deleteDocument(String id) {
        return delete("/documents/" + id, restTemplate);
    }

    // ------------------------------------------------------------------ transport

    /**
     * Headers for a read: the shared secret, nothing else.
     *
     * <p>RAGLab requires {@code X-Service-Token} on every endpoint once a token is
     * deployed, and answers {@code 401} without it — so the header goes on the
     * probe too, not only on the calls that carry a body. A deployment with no
     * token configured sends none, which is how an unauthenticated instance is
     * reached at all.
     */
    private HttpHeaders probeHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (serviceToken != null && !serviceToken.isBlank()) {
            headers.set(SERVICE_TOKEN_HEADER, serviceToken.trim());
        }
        return headers;
    }

    /** {@link #probeHeaders()} plus the content type a JSON body needs. */
    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = probeHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    public Result get(String path, RestTemplate template) {
        String url = baseUrl();
        long started = System.nanoTime();
        try {
            ResponseEntity<String> response = template.exchange(url + path, HttpMethod.GET,
                    new HttpEntity<>(probeHeaders()), String.class);
            return new Result(parse(response.getBody()), elapsedMs(started));
        } catch (HttpStatusCodeException e) {
            throw httpFailure(e, path, url);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new RagLabException(503, RagLabException.UNREACHABLE,
                    unreachableMessage(e, url), url);
        }
    }

    public Result delete(String path, RestTemplate template) {
        String url = baseUrl();
        long started = System.nanoTime();
        try {
            ResponseEntity<String> response = template.exchange(url + path, HttpMethod.DELETE,
                    new HttpEntity<>(probeHeaders()), String.class);
            return new Result(parse(response.getBody()), elapsedMs(started));
        } catch (HttpStatusCodeException e) {
            throw httpFailure(e, path, url);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new RagLabException(503, RagLabException.UNREACHABLE,
                    unreachableMessage(e, url), url);
        }
    }

    public Result post(String path, Object body, RestTemplate template) {
        String url = baseUrl();
        long started = System.nanoTime();
        try {
            HttpEntity<?> entity = body != null
                    ? new HttpEntity<>(body, jsonHeaders())
                    : new HttpEntity<>(probeHeaders());
            ResponseEntity<String> response = template.exchange(url + path, HttpMethod.POST,
                    entity, String.class);
            return new Result(parse(response.getBody()), elapsedMs(started));
        } catch (HttpStatusCodeException e) {
            throw httpFailure(e, path, url);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new RagLabException(503, RagLabException.UNREACHABLE,
                    unreachableMessage(e, url), url);
        }
    }

    /**
     * Turns a non-2xx reply into an exception the console can explain. FastAPI
     * carries a machine reason either at the top level or under {@code detail};
     * when it is absent we fall back to the status code, which is still better
     * than "500".
     */
    private RagLabException httpFailure(HttpStatusCodeException e, String path, String url) {
        String rawBody = e.getResponseBodyAsString();
        JsonNode parsed = parse(rawBody);
        String reason = firstText(parsed, "reason", "detail.reason", "error", "detail.code");
        String message = firstText(parsed, "message", "detail.message", "detail", "error");
        if (reason == null) {
            reason = "raglab_http_" + e.getStatusCode().value();
        }
        if (message == null || message.isBlank()) {
            message = "Le service a répondu " + e.getStatusCode().value() + " sur " + path + ".";
        }
        int status = e.getStatusCode().value();
        // Anything the service rejects on our side is a bad request; its own 5xx is
        // reported as a bad gateway, because from the operator's seat that is what
        // it is.
        return new RagLabException(status >= 500 ? 502 : status, reason, message, url);
    }

    /**
     * Best-effort JSON parse; a non-JSON body (an HTML error page) is not fatal.
     *
     * <p>Responses are read as {@code String} rather than deserialized straight
     * into {@link JsonNode}, because the converters Spring Boot 4 wires into
     * {@code RestTemplate} speak Jackson 3, which cannot produce Jackson 2
     * types: asking for {@code JsonNode.class} throws
     * {@code Type definition error: [simple type, class ...JsonNode]} the moment
     * a body is non-empty. So a successful reply used to fail on the way in —
     * the outward half of the same defect the DTO boundary already fixes on the
     * way out. Parsing here with our own Jackson 2 mapper keeps {@code JsonNode}
     * as the internal type the views read, which is what {@code AgentWire}
     * does with its own {@code String} responses.
     */
    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return json.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    /** First non-blank text among the given (possibly dotted) paths. */
    private static String firstText(JsonNode root, String... paths) {
        if (root == null) {
            return null;
        }
        for (String path : paths) {
            JsonNode node = root;
            for (String segment : path.split("\\.")) {
                node = node == null ? null : node.get(segment);
            }
            if (node != null && node.isValueNode() && !node.asText("").isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    /** The address every call will use; throws when nothing is configured. */
    public String baseUrl() {
        AgentEndpointService.EndpointView endpoint = endpoints.current();
        if (!endpoint.configured()) {
            throw new RagLabException(503, RagLabException.NOT_CONFIGURED,
                    "Aucune adresse de service RAG n'est configurée pour cet environnement.");
        }
        return endpoint.url();
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    /** Operator-readable cause, e.g. "Connection refused", prefixed with the target. */
    private static String unreachableMessage(RuntimeException e, String url) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String detail = cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName()
                : cause.getMessage();
        return "Connexion à " + url + " impossible : " + detail;
    }
}
