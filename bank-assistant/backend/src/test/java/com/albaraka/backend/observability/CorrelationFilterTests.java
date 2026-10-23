package com.albaraka.backend.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationFilterTests {

    private final CorrelationFilter filter = new CorrelationFilter();

    @Test
    void mintsARequestIdAndClearsMdcAfterwards() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/chat/x");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (r, s) -> seen.set(MDC.get(CorrelationFilter.MDC_REQUEST_ID));

        filter.doFilter(req, res, chain);

        assertThat(seen.get()).isNotBlank();
        assertThat(res.getHeader(CorrelationFilter.HEADER_REQUEST_ID)).isEqualTo(seen.get());
        assertThat(MDC.get(CorrelationFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    void honoursASaneInboundIdAndRejectsGarbage() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.addHeader(CorrelationFilter.HEADER_REQUEST_ID, "edge-42");
        req.addHeader(CorrelationFilter.HEADER_SESSION_ID, "bad id\nwith newline");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> session = new AtomicReference<>("unset");
        FilterChain chain = (r, s) -> session.set(MDC.get(CorrelationFilter.MDC_SESSION_ID));

        filter.doFilter(req, res, chain);

        assertThat(res.getHeader(CorrelationFilter.HEADER_REQUEST_ID)).isEqualTo("edge-42");
        assertThat(session.get()).isNull();
        assertThat(CorrelationFilter.sanitize("x".repeat(65))).isNull();
        assertThat(CorrelationFilter.sanitize("  ok-1.2:3_  ")).isEqualTo("ok-1.2:3_");
    }
}
