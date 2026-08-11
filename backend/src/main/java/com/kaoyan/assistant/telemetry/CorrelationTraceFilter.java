package com.kaoyan.assistant.telemetry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationTraceFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger("request.telemetry");
    private final ObjectMapper objectMapper;

    public CorrelationTraceFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        String correlationId = TraceContextHolder.validCorrelationIdOrNew(
                request.getHeader("X-Correlation-ID")
        );
        TraceContextHolder.ParsedTrace incoming = TraceContextHolder.parseTraceparent(
                request.getHeader("traceparent")
        );
        String traceId = incoming == null ? TraceContextHolder.randomHex(16) : incoming.traceId();
        String spanId = TraceContextHolder.randomHex(8);
        MDC.put(TraceContextHolder.CORRELATION_ID, correlationId);
        MDC.put(TraceContextHolder.TRACE_ID, traceId);
        MDC.put(TraceContextHolder.SPAN_ID, spanId);
        response.setHeader("X-Correlation-ID", correlationId);
        response.setHeader("traceparent", "00-" + traceId + "-" + spanId + "-01");
        try {
            filterChain.doFilter(request, response);
        } finally {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", "request_completed");
            event.put("service", "cs-kaoyan-backend");
            event.put("method", request.getMethod());
            event.put("path", request.getRequestURI());
            event.put("status", response.getStatus());
            event.put("duration_ms", Math.round((System.nanoTime() - started) / 1000.0) / 1000.0);
            event.put("correlation_id", correlationId);
            event.put("trace_id", traceId);
            event.put("span_id", spanId);
            event.put("parent_span_id", incoming == null ? null : incoming.parentSpanId());
            LOGGER.info(toJson(event));
            MDC.remove(TraceContextHolder.CORRELATION_ID);
            MDC.remove(TraceContextHolder.TRACE_ID);
            MDC.remove(TraceContextHolder.SPAN_ID);
        }
    }

    private String toJson(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException error) {
            return "{\"event\":\"request_completed\",\"serialization_error\":true}";
        }
    }
}
