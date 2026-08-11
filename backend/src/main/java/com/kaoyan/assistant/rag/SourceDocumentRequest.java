package com.kaoyan.assistant.rag;

import jakarta.validation.constraints.NotBlank;

public record SourceDocumentRequest(
        @NotBlank String title,
        String documentType,
        String sourceUrl,
        Long schoolId,
        Long collegeId,
        Long majorId,
        Integer year,
        String auditStatus,
        String sourceReliability,
        String rawText,
        String remark
) {
}
