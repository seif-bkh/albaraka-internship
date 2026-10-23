package com.albaraka.backend.services;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAGLab authenticates **every** endpoint with {@code X-Service-Token} once a
 * token is deployed, and answers {@code 401} without it.
 *
 * <p>The failure this guards against is quiet and misleading: a client that puts
 * the header on the calls with a body but not on the probe makes the console
 * report the service as *down* the day a token is configured, when what is
 * actually missing is a header. So the probe is asserted here too.
 *
 * <p>The other half of the contract is that an instance with no token
 * configured must still be reachable — hence the second test.
 */
class RagLabClientTokenTests {

    private static final String TOKEN = "raglab-service-token";

    private HttpServer server;
    private final AtomicReference<String> seenOnHealth = new AtomicReference<>();
    private final AtomicReference<String> seenOnAnswer = new AtomicReference<>();
    private final AtomicReference<String> seenOnModels = new AtomicReference<>();
    private final AtomicReference<String> seenOnDocuments = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            seenOnHealth.set(exchange.getRequestHeaders().getFirst("X-Service-Token"));
            respond(exchange, 200, "{\"status\":\"ok\"}");
        });
        server.createContext("/answer", exchange -> {
            seenOnAnswer.set(exchange.getRequestHeaders().getFirst("X-Service-Token"));
            respond(exchange, 200, "{\"status\":\"answered\",\"answer\":\"Réponse.\"}");
        });
        server.createContext("/models", exchange -> {
            seenOnModels.set(exchange.getRequestHeaders().getFirst("X-Service-Token"));
            respond(exchange, 200, "{\"embedding\":[],\"answer\":[]}");
        });
        server.createContext("/documents", exchange -> {
            seenOnDocuments.set(exchange.getRequestHeaders().getFirst("X-Service-Token"));
            respond(exchange, 200, "{\"documents\":[]}");
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void theTokenTravelsOnEveryCallNotOnlyOnThoseWithABody() {
        RagLabClient client = clientWith(TOKEN);

        client.health();
        client.answer("quel est le tarif ?", null, null, null, null, null);
        client.models();
        client.documents();

        assertThat(seenOnHealth.get()).isEqualTo(TOKEN);
        assertThat(seenOnAnswer.get()).isEqualTo(TOKEN);
        assertThat(seenOnModels.get()).isEqualTo(TOKEN);
        assertThat(seenOnDocuments.get()).isEqualTo(TOKEN);
    }

    @Test
    void withNoTokenDeployedNoHeaderIsSent() {
        RagLabClient client = clientWith("");

        client.health();
        client.answer("quel est le tarif ?", null, null, null, null, null);
        client.models();
        client.documents();

        assertThat(seenOnHealth.get()).isNull();
        assertThat(seenOnAnswer.get()).isNull();
        assertThat(seenOnModels.get()).isNull();
        assertThat(seenOnDocuments.get()).isNull();
    }

    /** A client pointed at the test server, with {@code token} as the deployed secret. */
    private RagLabClient clientWith(String token) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        return new RagLabClient(new FixedEndpoint(url), token);
    }

    private static void respond(HttpExchange exchange, int status, String body) {
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The client only ever asks the endpoint service where to call, so a
     * subclass that answers that one question stands in for the repository —
     * no mocking library, and no Spring context for a transport test.
     */
    private static final class FixedEndpoint extends AgentEndpointService {

        private final EndpointView view;

        FixedEndpoint(String url) {
            super(null, null, null);
            this.view = new EndpointView(url, SOURCE_ENV, url, null, null);
        }

        @Override
        public EndpointView current() {
            return view;
        }
    }
}
