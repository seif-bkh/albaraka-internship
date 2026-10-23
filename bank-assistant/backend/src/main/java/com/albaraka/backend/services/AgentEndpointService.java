package com.albaraka.backend.services;

import com.albaraka.backend.model.AgentEndpoint;
import com.albaraka.backend.repository.AgentEndpointRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the external RAG service lives, and who pointed the assistant there.
 *
 * <p>Resolution order: the newest admin-set row wins, otherwise the deployed
 * default ({@code RAGLAB_URL}), otherwise nothing — in which case the console
 * says so and no call is attempted. Addresses are validated before they are
 * stored, because the value is used to build URLs on every later call.
 */
@Service
public class AgentEndpointService {

    /** The address the admin set from the console. */
    public static final String SOURCE_DATABASE = "DATABASE";
    /** Fallback deployed with the application (env `RAGLAB_URL`). */
    public static final String SOURCE_ENV = "ENV";
    /** Nothing configured: the console is read-only and chat keeps its engine. */
    public static final String SOURCE_NONE = "NONE";

    /**
     * {@code scheme://host[:port][/path]} — IPv4, DNS name or a bracketed IPv6
     * literal; no credentials, no query, no fragment.
     */
    private static final Pattern ADDRESS = Pattern.compile(
            "^((?i:https?))://(\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9](?:[A-Za-z0-9._\\-]*[A-Za-z0-9])?)"
                    + "(:([0-9]{1,5}))?(/[A-Za-z0-9._~%+\\-/]*)?$");
    private static final int SCHEME_GROUP = 1;
    private static final int PORT_GROUP = 4;

    private static final int MAX_URL_LENGTH = 300;
    private static final int MAX_REASON_LENGTH = 512;

    private final AgentEndpointRepository repository;
    private final AgentAdminAuditService audit;
    private final String deployedUrl;

    public AgentEndpointService(AgentEndpointRepository repository,
                                AgentAdminAuditService audit,
                                @Value("${app.agent.raglab.url:}") String deployedUrl) {
        this.repository = repository;
        this.audit = audit;
        this.deployedUrl = deployedUrl;
    }

    /** The effective address, where it came from, and who last changed it. */
    public record EndpointView(String url, String source, String deployedUrl,
                               String updatedBy, LocalDateTime updatedAt) {
        public boolean configured() {
            return url != null && !url.isBlank();
        }
    }

    @Transactional(readOnly = true)
    public EndpointView current() {
        return repository.findFirstByOrderByUpdatedAtDescIdDesc()
                .map(row -> resolve(row.getUrl(), row.getUpdatedBy(), row.getUpdatedAt(), deployedUrl))
                .orElseGet(() -> resolve(null, null, null, deployedUrl));
    }

    /** Points the assistant at another address. Reason is mandatory (journal). */
    @Transactional
    public EndpointView set(String rawUrl, String reason, String actor) {
        String url = normalize(rawUrl);
        String motif = requireReason(reason);
        repository.save(new AgentEndpoint(url, motif, actor));
        audit.record("ENDPOINT_SET", url, "Adresse du service RAG modifiée depuis le back-office",
                motif, actor, "OK");
        return current();
    }

    /** Forgets the admin-set address and falls back to the deployed default. */
    @Transactional
    public EndpointView clear(String reason, String actor) {
        String motif = requireReason(reason);
        repository.deleteAll();
        audit.record("ENDPOINT_CLEARED", null, "Retour à l'adresse déployée (RAGLAB_URL)",
                motif, actor, "OK");
        return current();
    }

    // ------------------------------------------------------------------ pure logic

    /**
     * A stored row wins over the deployed default; a blank default means the
     * assistant has nowhere to call yet. Kept static so it can be tested without
     * a database.
     */
    public static EndpointView resolve(String storedUrl, String updatedBy, LocalDateTime updatedAt,
                                       String deployedUrl) {
        String fallback = deployedUrl == null || deployedUrl.isBlank() ? null : deployedUrl.trim();
        if (storedUrl != null && !storedUrl.isBlank()) {
            return new EndpointView(storedUrl.trim(), SOURCE_DATABASE, fallback, updatedBy, updatedAt);
        }
        if (fallback != null) {
            return new EndpointView(fallback, SOURCE_ENV, fallback, null, null);
        }
        return new EndpointView(null, SOURCE_NONE, null, null, null);
    }

    /**
     * Accepts what an operator would type for a production service — an
     * address and a port, optionally behind a path prefix — and rejects
     * everything else with a message meant to be shown as-is.
     *
     * <p>A missing scheme is interpreted as {@code http://}: operators type
     * {@code 10.20.30.40:8000} or {@code localhost:8000}, not a full URL, and
     * the console completes it instead of refusing the entry.
     *
     * <p>Validated with a pattern rather than {@link java.net.URI}: the parser
     * accepts shapes an operator would never mean ({@code http://} alone throws
     * instead of reporting a missing host), and the rules here are the ones the
     * console explains in its error text.
     */
    public static String normalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("L'adresse du service RAG est obligatoire.");
        }
        String raw = rawUrl.trim();
        if (raw.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("L'adresse ne peut pas contenir d'espace.");
        }
        // No scheme typed: assume http (the service is an internal network one).
        if (!raw.regionMatches(true, 0, "http://", 0, "http://".length())
                && !raw.regionMatches(true, 0, "https://", 0, "https://".length())) {
            raw = "http://" + raw;
        }
        if (raw.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("L'adresse ne peut pas dépasser " + MAX_URL_LENGTH + " caractères.");
        }

        Matcher matcher = ADDRESS.matcher(raw);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(explain(raw));
        }
        String port = matcher.group(PORT_GROUP);
        if (port != null) {
            int value = Integer.parseInt(port);
            if (value < 1 || value > 65_535) {
                throw new IllegalArgumentException("Le port doit être compris entre 1 et 65535 (reçu : " + port + ").");
            }
        }
        // Normalised form: lower-case scheme, no trailing slash. The rest of the
        // address is taken verbatim from the input (which matched as a whole), so
        // no capture-group arithmetic can lose the port or the path.
        int schemeLength = matcher.group(SCHEME_GROUP).length();
        String url = raw.substring(0, schemeLength).toLowerCase(Locale.ROOT) + raw.substring(schemeLength);
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /** Why an address was refused, in the operator's words. */
    private static String explain(String raw) {
        // `raw` always carries a scheme here (normalize completes it), so the
        // only remaining shapes are: missing host, credentials, or garbage.
        String afterScheme = raw.substring(raw.indexOf("://") + 3);
        if (afterScheme.isEmpty() || afterScheme.startsWith(":") || afterScheme.startsWith("/")) {
            return "L'adresse doit contenir un hôte et un port, par exemple http://10.20.30.40:8000.";
        }
        if (afterScheme.contains("@")) {
            return "L'adresse ne doit pas contenir d'identifiants.";
        }
        return "Adresse invalide : " + raw + " (attendu : http://hôte:port[/chemin]).";
    }

    /** {@code host:port} as an operator expects to read it (default ports filled in). */
    public static String hostPort(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return url.trim();
            }
            int port = uri.getPort();
            if (port < 0) {
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }
            return host + ":" + port;
        } catch (URISyntaxException e) {
            return url.trim();
        }
    }

    /** Trimmed reason, or a 400-worthy complaint — mutations are never anonymous. */
    public static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Un motif est obligatoire pour journaliser ce changement.");
        }
        String motif = reason.trim();
        if (motif.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Le motif ne peut pas dépasser " + MAX_REASON_LENGTH + " caractères.");
        }
        return motif;
    }
}
