package com.kaoyan.assistant.ai;

public record InternalAgentEvidenceResult(
        Long documentId,
        int chunkCount,
        boolean created,
        Long targetId,
        String targetStatus
) {
}
