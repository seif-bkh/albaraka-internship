package com.albaraka.backend.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Binds a per-request correlation id to the logging context (MDC) and echoes it
 * back as {@code X-Request-Id} so a support ticket can quote one id that finds
 * every log line of that request (Phase 6.1).
 *
 * <p>An inbound {@code X-Request-Id} is honoured when it looks sane (short,
 * printable) so an edge proxy can originate the id; otherwise one is minted.
 * The chat session id is also placed in MDC when the client sends it, and
 * {@link com.albaraka.backend.services.AgentWire} adds {@code turnId} for the
 * agent call. All keys are cleared in {@code finally} because the servlet thread
 * is pooled.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_SESSION_ID = "X-Session-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_SESSION_ID = "sessionId";

    private static final int MAX_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = sanitize(request.getHeader(HEADER_REQUEST_ID));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        String sessionId = sanitize(request.getHeader(HEADER_SESSION_ID));
        MDC.put(MDC_REQUEST_ID, requestId);
        if (sessionId != null) {
            MDC.put(MDC_SESSION_ID, sessionId);
        }
        response.setHeader(HEADER_REQUEST_ID, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    /** Accepts only short, printable ASCII ids; anything else is treated as absent. */
    static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.length() > MAX_ID_LENGTH) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || c == '-' || c == '_' || c == '.' || c == ':';
            if (!ok) {
                return null;
            }
        }
        return value;
    }
}
