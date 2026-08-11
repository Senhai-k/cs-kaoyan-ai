package com.kaoyan.assistant.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOpsServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void combinesHealthAndMetricsIntoPublicStatus() throws Exception {
        AgentGateway gateway = mock(AgentGateway.class);
        when(gateway.get("/api/health")).thenReturn(objectMapper.readTree("""
                {
                  "status":"UP","framework":"LangGraph","capabilities":["StateGraph","ToolNode"],
                  "indexedChunks":130,"embeddingModel":"bge-small-zh-v1.5",
                  "rerankerEnabled":true,"rerankerMode":"feature","generationMode":"grounded-extractive",
                  "plannerLlm":{"configured":true,"experimentReady":true,"status":"READY","model":"planner-model","endpointType":"custom","pricingMode":"UNMETERED","missingConfiguration":[],"pricingUnit":"UNMETERED"},
                  "otlpExporterEnabled":true
                }
                """));
        when(gateway.get("/api/metrics")).thenReturn(objectMapper.readTree("""
                {
                  "total_tasks":20,"completed_tasks":19,"waiting_tasks":1,"failed_tasks":0,
                  "tool_calls":20,"successful_tool_calls":20,"average_latency_ms":87.5,
                  "task_completion_rate":0.95,"tool_success_rate":1.0
                }
                """));

        AgentStatusResponse status = new AgentOpsService(gateway).status();

        assertThat(status.available()).isTrue();
        assertThat(status.indexedChunks()).isEqualTo(130);
        assertThat(status.rerankerMode()).isEqualTo("feature");
        assertThat(status.plannerLlm().experimentReady()).isTrue();
        assertThat(status.plannerLlm().model()).isEqualTo("planner-model");
        assertThat(status.plannerLlm().pricingMode()).isEqualTo("UNMETERED");
        assertThat(status.otlpExporterEnabled()).isTrue();
        assertThat(status.metrics().taskCompletionRate()).isEqualTo(0.95);
    }

    @Test
    void reportsDownWithoutBreakingStatusEndpoint() {
        AgentGateway gateway = mock(AgentGateway.class);
        when(gateway.get("/api/health")).thenThrow(new IllegalStateException("connection refused"));

        AgentStatusResponse status = new AgentOpsService(gateway).status();

        assertThat(status.available()).isFalse();
        assertThat(status.status()).isEqualTo("DOWN");
        assertThat(status.message()).contains("connection refused");
    }

    @Test
    void proxiesCoverageWorkflowTelemetry() throws Exception {
        AgentGateway gateway = mock(AgentGateway.class);
        when(gateway.get("/api/workflows/coverage/runs?limit=8"))
                .thenReturn(objectMapper.readTree("[{\"thread_id\":\"run-1\"}]"));
        when(gateway.get("/api/workflows/coverage/metrics"))
                .thenReturn(objectMapper.readTree("{\"total_runs\":1}"));
        when(gateway.post("/api/audit/knowledge/run", null))
                .thenReturn(objectMapper.readTree("{\"total_documents\":133}"));
        when(gateway.get("/api/operations/jobs?limit=5"))
                .thenReturn(objectMapper.readTree("[{\"id\":\"job-1\"}]"));
        when(gateway.get("/api/operations/jobs/job-1"))
                .thenReturn(objectMapper.readTree("{\"id\":\"job-1\",\"status\":\"COMPLETED\"}"));
        when(gateway.get("/api/operations/diagnostics?query=duplicate+url&category=PLANNER_FILTER&severity=ALL&limit=20"))
                .thenReturn(objectMapper.readTree("{\"total\":1}"));
        when(gateway.post("/api/operations/jobs/job-1/retry", null))
                .thenReturn(objectMapper.readTree("{\"attempt\":2}"));
        AgentOpsService service = new AgentOpsService(gateway);

        assertThat(service.coverageWorkflowRuns(8).get(0).path("thread_id").asText()).isEqualTo("run-1");
        assertThat(service.coverageWorkflowMetrics().path("total_runs").asInt()).isEqualTo(1);
        assertThat(service.runKnowledgeAudit().path("total_documents").asInt()).isEqualTo(133);
        assertThat(service.operationJobs(5).get(0).path("id").asText()).isEqualTo("job-1");
        assertThat(service.operationJob("job-1").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(service.diagnostics("duplicate url", "PLANNER_FILTER", "ALL", 20)
                .path("total").asInt()).isEqualTo(1);
        assertThat(service.retryOperationJob("job-1").path("attempt").asInt()).isEqualTo(2);
    }
}
