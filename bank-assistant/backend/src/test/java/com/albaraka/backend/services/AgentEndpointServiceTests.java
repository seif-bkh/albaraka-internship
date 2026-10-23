package com.albaraka.backend.services;

import com.albaraka.backend.services.AgentEndpointService.EndpointView;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The address rules the console relies on: what is accepted, what the operator
 * is told when it is not, and which value actually wins.
 */
class AgentEndpointServiceTests {

    private static final String PROD = "http://10.20.30.40:8000";

    @Test
    void acceptsAnAddressAndPortAndDropsTheTrailingSlash() {
        assertEquals(PROD, AgentEndpointService.normalize("http://10.20.30.40:8000"));
        assertEquals(PROD, AgentEndpointService.normalize("  " + PROD + "/  "));
        assertEquals("https://raglab.bank.internal:8443/rag",
                AgentEndpointService.normalize("https://raglab.bank.internal:8443/rag/"));
        // A path prefix (a service behind a reverse proxy), an IPv6 literal, and a
        // scheme an operator typed in capitals are all legitimate.
        assertEquals("http://raglab.bank.internal:8000/rag", AgentEndpointService.normalize("http://raglab.bank.internal:8000/rag"));
        assertEquals("http://[::1]:8000", AgentEndpointService.normalize("http://[::1]:8000"));
        assertEquals(PROD, AgentEndpointService.normalize("HTTP://10.20.30.40:8000"));
        // A scheme typed in capitals is case-normalised, never changed: an operator
        // who typed https keeps TLS. Expecting http here would ask normalize() to
        // silently downgrade the address we call the RAG service on.
        assertEquals("https://10.20.30.40:8000", AgentEndpointService.normalize("HTTPS://10.20.30.40:8000"));
    }

    @Test
    void aMissingSchemeIsInterpretedAsHttp() {
        // Operators type the address as they read it, without a scheme — the
        // console completes it instead of rejecting the entry.
        assertEquals("http://localhost:8000", AgentEndpointService.normalize("localhost:8000"));
        assertEquals(PROD, AgentEndpointService.normalize("10.20.30.40:8000"));
        assertEquals("http://10.20.30.40", AgentEndpointService.normalize("10.20.30.40"));
        assertEquals("http://raglab.bank.internal:8000/rag", AgentEndpointService.normalize("raglab.bank.internal:8000/rag/"));
        assertEquals("https://raglab.bank.internal:8443", AgentEndpointService.normalize("https://raglab.bank.internal:8443"));
    }

    @Test
    void rejectsWhatCannotBeCalledAndSaysWhy() {
        // A scheme the service cannot be called with, or a host that is missing
        // entirely, are still refused — only a *missing* http(s) scheme is
        // completed silently (see aMissingSchemeIsInterpretedAsHttp).
        IllegalArgumentException badScheme = assertThrows(IllegalArgumentException.class,
                () -> AgentEndpointService.normalize("ftp://10.20.30.40:8000"));
        assertTrue(badScheme.getMessage().contains("invalide"), badScheme.getMessage());
        IllegalArgumentException noHostColon = assertThrows(IllegalArgumentException.class,
                () -> AgentEndpointService.normalize(":8000"));
        assertTrue(noHostColon.getMessage().contains("hôte"), noHostColon.getMessage());
        IllegalArgumentException noHost = assertThrows(IllegalArgumentException.class,
                () -> AgentEndpointService.normalize("http://"));
        assertTrue(noHost.getMessage().contains("hôte"), noHost.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> AgentEndpointService.normalize("http://user:secret@10.20.30.40:8000"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentEndpointService.normalize("http://10.20.30.40:8000?token=abc"));
        assertThrows(IllegalArgumentException.class, () -> AgentEndpointService.normalize("http://a b:8000"));
        assertThrows(IllegalArgumentException.class, () -> AgentEndpointService.normalize("   "));
        assertThrows(IllegalArgumentException.class, () -> AgentEndpointService.normalize(null));

        // A port outside the range is refused before any call is attempted.
        IllegalArgumentException badPort = assertThrows(IllegalArgumentException.class,
                () -> AgentEndpointService.normalize("http://10.20.30.40:99999"));
        assertTrue(badPort.getMessage().contains("port"), badPort.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> AgentEndpointService.normalize("http://10.20.30.40:8000/" + "x".repeat(400)));
    }

    @Test
    void aStoredAddressWinsOverTheDeployedDefault() {
        LocalDateTime when = LocalDateTime.of(2026, 9, 15, 10, 30);
        EndpointView stored = AgentEndpointService.resolve("http://10.0.0.9:9000", "admin@albaraka.tn",
                when, PROD);

        assertEquals("http://10.0.0.9:9000", stored.url());
        assertEquals(AgentEndpointService.SOURCE_DATABASE, stored.source());
        assertEquals(PROD, stored.deployedUrl(), "the screen still offers the deployed value");
        assertEquals("admin@albaraka.tn", stored.updatedBy());
        assertEquals(when, stored.updatedAt());
        assertTrue(stored.configured());
    }

    @Test
    void withoutARowTheDeployedDefaultAppliesAndWithoutBothNothingIsConfigured() {
        EndpointView deployed = AgentEndpointService.resolve(null, null, null, PROD);
        assertEquals(PROD, deployed.url());
        assertEquals(AgentEndpointService.SOURCE_ENV, deployed.source());
        assertNull(deployed.updatedBy());
        assertTrue(deployed.configured());

        EndpointView nothing = AgentEndpointService.resolve("  ", null, null, "");
        assertEquals(AgentEndpointService.SOURCE_NONE, nothing.source());
        assertNull(nothing.url());
        assertFalse(nothing.configured(), "an unconfigured assistant must not pretend to have a target");
    }

    @Test
    void hostPortReadsLikeAnOperatorWritesIt() {
        assertEquals("10.20.30.40:8000", AgentEndpointService.hostPort(PROD));
        assertEquals("raglab.bank.internal:8443", AgentEndpointService.hostPort("https://raglab.bank.internal:8443/rag"));
        assertEquals("raglab.bank.internal:443", AgentEndpointService.hostPort("https://raglab.bank.internal"));
        assertEquals("10.20.30.40:80", AgentEndpointService.hostPort("http://10.20.30.40"));
        assertNull(AgentEndpointService.hostPort(null));
    }

    @Test
    void everyMutationCarriesAReason() {
        assertEquals("pilotage RAGLab", AgentEndpointService.requireReason("  pilotage RAGLab "));
        assertThrows(IllegalArgumentException.class, () -> AgentEndpointService.requireReason(null));
        assertThrows(IllegalArgumentException.class, () -> AgentEndpointService.requireReason("   "));
        assertThrows(IllegalArgumentException.class,
                () -> AgentEndpointService.requireReason("x".repeat(600)));
    }
}
