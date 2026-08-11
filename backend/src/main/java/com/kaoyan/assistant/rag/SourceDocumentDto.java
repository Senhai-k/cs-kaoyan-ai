package com.kaoyan.assistant.rag;

public record SourceDocumentDto(
        Long id,
        String title,
        String documentType,
        String sourceUrl,
        Long schoolId,
        Long collegeId,
        Long majorId,
        Integer year,
        String auditStatus,
        String sourceReliability,
        String rawText,
        String remark,
        String updatedAt
) {
}
