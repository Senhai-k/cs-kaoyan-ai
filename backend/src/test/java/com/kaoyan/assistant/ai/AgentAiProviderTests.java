package com.kaoyan.assistant.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAiProviderTests {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsCompletedLangGraphResponse() throws Exception {
        AtomicReference<String> correlationHeader = new AtomicReference<>();
        AtomicReference<String> traceparentHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/agent/query", exchange -> {
            correlationHeader.set(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
            traceparentHeader.set(exchange.getRequestHeaders().getFirst("traceparent"));
            String response = """
                    {
                      "status":"COMPLETED",
                      "answer":"北邮 2026 年该方向初试科目包含 408 [1]",
                      "sources":["[1] 北京邮电大学 / 2026 / 硕士专业目录"],
                      "related_school_id":13,
                      "thread_id":"thread-1","confidence":0.95,"route":"completed","retrieval_count":5,
                      "trace":["plan:tool=hybrid_retrieve","tool:hybrid_retrieve"]
                    }
                    """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        AgentGateway gateway = new AgentGateway(
                "http://127.0.0.1:" + server.getAddress().getPort(), 10, new ObjectMapper());
        AgentAiProvider provider = new AgentAiProvider(gateway);
        MDC.put("correlation_id", "request-789");
        MDC.put("trace_id", "fedcba9876543210fedcba9876543210");
        AiProviderResult result;
        try {
            result = provider.answer("北京邮电大学计算机考408吗？");
        } finally {
            MDC.clear();
        }

        assertThat(result.answer()).contains("408 [1]");
        assertThat(result.relatedSchoolId()).isEqualTo(13L);
        assertThat(result.sources()).containsExactly("[1] 北京邮电大学 / 2026 / 硕士专业目录");
        assertThat(result.meta().provider()).isEqualTo("langgraph");
        assertThat(result.meta().confidence()).isEqualTo(0.95);
        assertThat(result.meta().retrievalCount()).isEqualTo(5);
        assertThat(correlationHeader.get()).isEqualTo("request-789");
        assertThat(traceparentHeader.get())
                .startsWith("00-fedcba9876543210fedcba9876543210-")
                .endsWith("-01");
    }
}
