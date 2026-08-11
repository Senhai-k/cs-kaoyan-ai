package com.kaoyan.assistant.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationTraceFilterTests {

    @Test
    void preservesIncomingTraceAndReplacesInvalidCorrelationId() throws Exception {
        CorrelationTraceFilter filter = new CorrelationTraceFilter(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/schools");
        request.addHeader("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
        request.addHeader("X-Correlation-ID", "invalid value with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (currentRequest, currentResponse) ->
                ((MockHttpServletResponse) currentResponse).setStatus(204));

        assertThat(response.getHeader("traceparent"))
                .startsWith("00-0123456789abcdef0123456789abcdef-")
                .endsWith("-01");
        assertThat(response.getHeader("X-Correlation-ID")).matches("[0-9a-f]{32}");
    }
}
