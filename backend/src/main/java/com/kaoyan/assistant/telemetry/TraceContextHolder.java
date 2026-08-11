package com.kaoyan.assistant.telemetry;

import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TraceContextHolder {

    public static final String CORRELATION_ID = "correlation_id";
    public static final String TRACE_ID = "trace_id";
    public static final String SPAN_ID = "span_id";
    private static final Pattern TRACEPARENT = Pattern.compile(
            "^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$"
    );
    private static final Pattern CORRELATION = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceContextHolder() {
    }

    public static String validCorrelationIdOrNew(String value) {
        return value != null && CORRELATION.matcher(value).matches() ? value : randomHex(16);
    }

    public static ParsedTrace parseTraceparent(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = TRACEPARENT.matcher(value.strip().toLowerCase());
        if (!matcher.matches()
                || matcher.group(1).equals("0".repeat(32))
                || matcher.group(2).equals("0".repeat(16))) {
            return null;
        }
        return new ParsedTrace(matcher.group(1), matcher.group(2));
    }

    public static String currentCorrelationIdOrNew() {
        String value = MDC.get(CORRELATION_ID);
        return value == null || value.isBlank() ? randomHex(16) : value;
    }

    public static String currentTraceIdOrNew() {
        String value = MDC.get(TRACE_ID);
        return value == null || value.isBlank() ? randomHex(16) : value;
    }

    public static String childTraceparent() {
        return "00-" + currentTraceIdOrNew() + "-" + randomHex(8) + "-01";
    }

    public static String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public record ParsedTrace(String traceId, String parentSpanId) {
    }
}
