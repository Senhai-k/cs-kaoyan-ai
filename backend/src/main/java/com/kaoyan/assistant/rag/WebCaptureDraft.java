package com.kaoyan.assistant.rag;

public record WebCaptureDraft(
        Long captureTaskId,
        Long targetId,
        Long schoolId,
        String title,
        String documentType,
        Integer year,
        String sourceUrl,
        String rawText,
        String remark,
        String contentSha256,
        boolean duplicate,
        String extractorVersion,
        boolean changeDetected,
        Long changeId
) {
}
