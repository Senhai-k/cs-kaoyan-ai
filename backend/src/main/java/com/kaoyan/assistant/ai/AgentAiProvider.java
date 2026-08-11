package com.kaoyan.assistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "agent")
public class AgentAiProvider implements AiProvider {

    private final AgentGateway gateway;

    public AgentAiProvider(AgentGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public AiProviderResult answer(String question) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", question);
        body.put("allow_human_review", false);
        return mapResponse(gateway.post("/api/agent/query", body));
    }

    private AiProviderResult mapResponse(JsonNode payload) {
        String status = payload.path("status").asText();
        String answer = payload.path("answer").asText();
        if (answer.isBlank() || !("COMPLETED".equals(status) || "REJECTED".equals(status))) {
            throw new IllegalStateException("agent returned an incomplete result with status " + status);
        }
        List<String> sources = new ArrayList<>();
        payload.path("sources").forEach(source -> sources.add(source.asText()));
        JsonNode schoolIdNode = payload.path("related_school_id");
        Long relatedSchoolId = schoolIdNode.isIntegralNumber() ? schoolIdNode.longValue() : null;
        String sourceSummary = sources.isEmpty() ? "LangGraph 私域知识库" : String.join("；", sources);
        List<String> trace = new ArrayList<>();
        payload.path("trace").forEach(item -> trace.add(item.asText()));
        AiExecutionMeta meta = new AiExecutionMeta(
                "langgraph",
                payload.path("thread_id").asText(null),
                status,
                payload.path("confidence").asDouble(),
                payload.path("route").asText(""),
                payload.path("retrieval_count").asInt(),
                trace
        );
        return new AiProviderResult(answer, relatedSchoolId, sourceSummary, sources, meta);
    }
}
