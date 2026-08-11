package com.kaoyan.assistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class AgentOpsService {

    private final AgentGateway gateway;

    public AgentOpsService(AgentGateway gateway) {
        this.gateway = gateway;
    }

    public AgentStatusResponse status() {
        try {
            JsonNode health = gateway.get("/api/health");
            JsonNode metrics = gateway.get("/api/metrics");
            List<String> capabilities = new ArrayList<>();
            health.path("capabilities").forEach(item -> capabilities.add(item.asText()));
            return new AgentStatusResponse(
                    "UP".equalsIgnoreCase(health.path("status").asText()),
                    health.path("status").asText("UNKNOWN"),
                    health.path("framework").asText(""),
                    capabilities,
                    health.path("indexedChunks").asInt(),
                    health.path("embeddingModel").asText(""),
                    health.path("rerankerEnabled").asBoolean(),
                    health.path("rerankerMode").asText("off"),
                    health.path("generationMode").asText(""),
                    mapPlannerLlm(health.path("plannerLlm")),
                    health.path("otlpExporterEnabled").asBoolean(),
                    mapMetrics(metrics),
                    ""
            );
        } catch (RuntimeException error) {
            return new AgentStatusResponse(
                    false, "DOWN", "LangGraph", List.of(), 0, "", false, "off", "",
                    new AgentStatusResponse.PlannerLlmStatus(false, false, "UNAVAILABLE", "", "", "METERED", List.of(), "USD_PER_MILLION_TOKENS"), false,
                    emptyMetrics(), error.getMessage()
            );
        }
    }

    public JsonNode syncIndex() {
        return gateway.post("/api/index/sync", null);
    }

    public JsonNode runEvaluation() {
        return gateway.post("/api/evaluation/run", null);
    }

    public JsonNode runCoverageEvaluation() {
        return gateway.post("/api/evaluation/coverage/run", null);
    }

    public JsonNode runKnowledgeAudit() {
        return gateway.post("/api/audit/knowledge/run", null);
    }

    public JsonNode startCoverageWorkflow(Object request) {
        return gateway.post("/api/workflows/coverage/start", request);
    }

    public JsonNode resumeCoverageWorkflow(String threadId, Object request) {
        return gateway.post("/api/workflows/coverage/" + threadId + "/resume", request);
    }

    public JsonNode coverageWorkflowRuns(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return gateway.get("/api/workflows/coverage/runs?limit=" + safeLimit);
    }

    public JsonNode coverageWorkflowMetrics() {
        return gateway.get("/api/workflows/coverage/metrics");
    }

    public JsonNode startOperationJob(Object request) {
        return gateway.post("/api/operations/jobs", request);
    }

    public JsonNode operationJobs(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return gateway.get("/api/operations/jobs?limit=" + safeLimit);
    }

    public JsonNode diagnostics(String query, String category, String severity, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return gateway.get("/api/operations/diagnostics?query=" + encode(query)
                + "&category=" + encode(category) + "&severity=" + encode(severity)
                + "&limit=" + safeLimit);
    }

    public JsonNode operationJob(String jobId) {
        return gateway.get("/api/operations/jobs/" + jobId);
    }

    public JsonNode operationJobTrace(String jobId) {
        return gateway.get("/api/operations/jobs/" + jobId + "/trace");
    }

    public JsonNode cancelOperationJob(String jobId) {
        return gateway.post("/api/operations/jobs/" + jobId + "/cancel", null);
    }

    public JsonNode retryOperationJob(String jobId) {
        return gateway.post("/api/operations/jobs/" + jobId + "/retry", null);
    }

    public JsonNode telemetryTraces(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return gateway.get("/api/telemetry/traces?limit=" + safeLimit);
    }

    public JsonNode telemetryTrace(String traceId) {
        return gateway.get("/api/telemetry/traces/" + traceId);
    }

    private AgentStatusResponse.Metrics mapMetrics(JsonNode metrics) {
        return new AgentStatusResponse.Metrics(
                metrics.path("total_tasks").asInt(),
                metrics.path("completed_tasks").asInt(),
                metrics.path("waiting_tasks").asInt(),
                metrics.path("failed_tasks").asInt(),
                metrics.path("tool_calls").asInt(),
                metrics.path("successful_tool_calls").asInt(),
                metrics.path("average_latency_ms").asDouble(),
                metrics.path("task_completion_rate").asDouble(),
                metrics.path("tool_success_rate").asDouble()
        );
    }

    private AgentStatusResponse.Metrics emptyMetrics() {
        return new AgentStatusResponse.Metrics(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private AgentStatusResponse.PlannerLlmStatus mapPlannerLlm(JsonNode node) {
        List<String> missing = new ArrayList<>();
        node.path("missingConfiguration").forEach(item -> missing.add(item.asText()));
        return new AgentStatusResponse.PlannerLlmStatus(
                node.path("configured").asBoolean(),
                node.path("experimentReady").asBoolean(),
                node.path("status").asText("UNKNOWN"),
                node.path("model").asText(""),
                node.path("endpointType").asText(""),
                node.path("pricingMode").asText("METERED"),
                missing,
                node.path("pricingUnit").asText("USD_PER_MILLION_TOKENS")
        );
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
