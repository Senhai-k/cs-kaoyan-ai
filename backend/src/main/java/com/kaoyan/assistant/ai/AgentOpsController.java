package com.kaoyan.assistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.assistant.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/ai/agent")
public class AgentOpsController {

    private final AgentOpsService service;

    public AgentOpsController(AgentOpsService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public ApiResponse<AgentStatusResponse> status() {
        return ApiResponse.success(service.status());
    }

    @PostMapping("/operations/index-sync")
    public ApiResponse<JsonNode> syncIndex() {
        return ApiResponse.success(service.syncIndex());
    }

    @PostMapping("/operations/evaluation")
    public ApiResponse<JsonNode> runEvaluation() {
        return ApiResponse.success(service.runEvaluation());
    }

    @PostMapping("/operations/coverage-evaluation")
    public ApiResponse<JsonNode> runCoverageEvaluation() {
        return ApiResponse.success(service.runCoverageEvaluation());
    }

    @PostMapping("/operations/knowledge-audit")
    public ApiResponse<JsonNode> runKnowledgeAudit() {
        return ApiResponse.success(service.runKnowledgeAudit());
    }

    @PostMapping("/operations/coverage-workflows")
    public ApiResponse<JsonNode> startCoverageWorkflow(@RequestBody JsonNode request) {
        return ApiResponse.success(service.startCoverageWorkflow(request));
    }

    @PostMapping("/operations/coverage-workflows/{threadId}/resume")
    public ApiResponse<JsonNode> resumeCoverageWorkflow(@PathVariable String threadId,
                                                        @RequestBody JsonNode request) {
        return ApiResponse.success(service.resumeCoverageWorkflow(threadId, request));
    }

    @GetMapping("/operations/coverage-workflows/runs")
    public ApiResponse<JsonNode> coverageWorkflowRuns(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(service.coverageWorkflowRuns(limit));
    }

    @GetMapping("/operations/coverage-workflows/metrics")
    public ApiResponse<JsonNode> coverageWorkflowMetrics() {
        return ApiResponse.success(service.coverageWorkflowMetrics());
    }

    @PostMapping("/operations/jobs")
    public ApiResponse<JsonNode> startOperationJob(@RequestBody JsonNode request) {
        return ApiResponse.success(service.startOperationJob(request));
    }

    @GetMapping("/operations/jobs")
    public ApiResponse<JsonNode> operationJobs(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(service.operationJobs(limit));
    }

    @GetMapping("/operations/diagnostics")
    public ApiResponse<JsonNode> diagnostics(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "ALL") String severity,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.success(service.diagnostics(query, category, severity, limit));
    }

    @GetMapping("/operations/jobs/{jobId}")
    public ApiResponse<JsonNode> operationJob(@PathVariable String jobId) {
        return ApiResponse.success(service.operationJob(jobId));
    }

    @GetMapping("/operations/jobs/{jobId}/trace")
    public ApiResponse<JsonNode> operationJobTrace(@PathVariable String jobId) {
        return ApiResponse.success(service.operationJobTrace(jobId));
    }

    @PostMapping("/operations/jobs/{jobId}/cancel")
    public ApiResponse<JsonNode> cancelOperationJob(@PathVariable String jobId) {
        return ApiResponse.success(service.cancelOperationJob(jobId));
    }

    @PostMapping("/operations/jobs/{jobId}/retry")
    public ApiResponse<JsonNode> retryOperationJob(@PathVariable String jobId) {
        return ApiResponse.success(service.retryOperationJob(jobId));
    }

    @GetMapping("/operations/telemetry/traces")
    public ApiResponse<JsonNode> telemetryTraces(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(service.telemetryTraces(limit));
    }

    @GetMapping("/operations/telemetry/traces/{traceId}")
    public ApiResponse<JsonNode> telemetryTrace(@PathVariable String traceId) {
        return ApiResponse.success(service.telemetryTrace(traceId));
    }
}
