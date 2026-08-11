package com.kaoyan.assistant.rag;

public record SourceDocumentVersionDto(
        Long id,
        Long documentId,
        Integer versionNo,
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
        String operation,
        String operator,
        String sourceUpdatedAt,
        String createdAt
) {
    public SourceDocumentRequest toRequest() {
        return new SourceDocumentRequest(
                title, documentType, sourceUrl, schoolId, collegeId, majorId, year,
                auditStatus, sourceReliability, rawText, remark
        );
    }
}
