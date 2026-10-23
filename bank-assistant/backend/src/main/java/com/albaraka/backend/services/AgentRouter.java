package com.albaraka.backend.services;

import com.albaraka.backend.services.AgentWire;
import com.albaraka.backend.services.AgentWire.AgentReply;
import com.albaraka.backend.services.AgentWire.StreamListener;
import com.albaraka.backend.services.AgentWire.Turn;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * The one place the serving path names an engine.
 *
 * <p>RAGLab is the only engine the gateway talks to. The seam stays because it is
 * what the controllers and the console depend on: quotas, metering, persistence and
 * SSE framing call {@code invoke}/{@code invokeStream} and never touch a concrete
 * client. If a second engine ever returns, it is added here, not in the edge.
 */
@Service
public class AgentRouter {

    /** Wire name of the engine every turn goes to. */
    public static final String PROVIDER = RagLabServingAgent.PROVIDER;

    private final RagLabServingAgent agent;

    public AgentRouter(RagLabServingAgent agent) {
        this.agent = agent;
    }

    /** Wire name of the engine serving right now. */
    public String provider() {
        return PROVIDER;
    }

    public AgentReply invoke(String sessionId, String message, String language, List<Turn> history) {
        return agent.invoke(sessionId, message, language, history);
    }

    public void invokeStream(String sessionId, String message, String language, List<Turn> history,
                             StreamListener listener) {
        agent.invokeStream(sessionId, message, language, history, listener);
    }

    public Map<String, Object> explain(String message, String language) {
        return agent.explain(message, language);
    }
}
