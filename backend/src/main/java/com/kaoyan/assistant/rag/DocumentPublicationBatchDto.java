package com.kaoyan.assistant.rag;

public record DocumentPublicationBatchDto(
        Long id,
        String status,
        Integer documentCount,
        Integer chunkCount,
        Integer rollbackChunkCount,
        String reason,
        String operator,
        String rollbackReason,
        String rollbackOperator,
        String createdAt,
        String completedAt,
        String rolledBackAt
) {
}
