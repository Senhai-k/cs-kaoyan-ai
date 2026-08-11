package com.kaoyan.assistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaoyan.assistant.telemetry.TraceContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class AgentGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentGateway.class);

    private final String endpoint;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AgentGateway(
            @Value("${app.ai.agent.endpoint:http://127.0.0.1:18889}") String endpoint,
            @Value("${app.ai.agent.timeout-seconds:90}") long timeoutSeconds,
            ObjectMapper objectMapper
    ) {
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public JsonNode get(String path) {
        return exchange(path, "GET", null);
    }

    public JsonNode post(String path, Object body) {
        return exchange(path, "POST", body);
    }

    private JsonNode exchange(String path, String method, Object body) {
        long started = System.nanoTime();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint + path))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("X-Correlation-ID", TraceContextHolder.currentCorrelationIdOrNew())
                    .header("traceparent", TraceContextHolder.childTraceparent());
            if ("POST".equals(method)) {
                String json = body == null ? "" : objectMapper.writeValueAsString(body);
                builder.header("Content-Type", "application/json; charset=utf-8")
                        .POST(json.isEmpty()
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(json));
            } else {
                builder.GET();
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            LOGGER.info("agent_request_completed method={} path={} status={} duration_ms={}",
                    method, path, response.statusCode(),
                    Math.round((System.nanoTime() - started) / 1000.0) / 1000.0);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("agent returned HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("agent request was interrupted", error);
        } catch (Exception error) {
            throw new IllegalStateException("agent request failed: " + error.getMessage(), error);
        }
    }
}
