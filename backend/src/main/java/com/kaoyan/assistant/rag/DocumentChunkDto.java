package com.kaoyan.assistant.rag;

public record DocumentChunkDto(
        Long id,
        Long documentId,
        Long schoolId,
        Long collegeId,
        Long majorId,
        Integer year,
        String documentType,
        Integer chunkIndex,
        String content,
        Integer pageNumber,
        String auditStatus,
        String updatedAt
) {
}
