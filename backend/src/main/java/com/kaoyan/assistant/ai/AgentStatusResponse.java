package com.kaoyan.assistant.ai;

import java.util.List;

public record AgentStatusResponse(
        boolean available,
        String status,
        String framework,
        List<String> capabilities,
        int indexedChunks,
        String embeddingModel,
        boolean rerankerEnabled,
        String rerankerMode,
        String generationMode,
        PlannerLlmStatus plannerLlm,
        boolean otlpExporterEnabled,
        Metrics metrics,
        String message
) {
    public record PlannerLlmStatus(
            boolean configured,
            boolean experimentReady,
            String status,
            String model,
            String endpointType,
            String pricingMode,
            List<String> missingConfiguration,
            String pricingUnit
    ) {
    }

    public record Metrics(
            int totalTasks,
            int completedTasks,
            int waitingTasks,
            int failedTasks,
            int toolCalls,
            int successfulToolCalls,
            double averageLatencyMs,
            double taskCompletionRate,
            double toolSuccessRate
    ) {
    }
}
