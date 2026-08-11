package com.kaoyan.assistant.rag;

public record WebCaptureTaskDto(
        Long id,
        Long targetId,
        String requestedUrl,
        String finalUrl,
        String contentSha256,
        Integer httpStatus,
        String contentType,
        Long responseSize,
        String extractorVersion,
        String status,
        String title,
        Integer extractedLength,
        Integer reuseCount,
        String errorMessage,
        String operator,
        String createdAt,
        String updatedAt,
        String completedAt
) {
}
