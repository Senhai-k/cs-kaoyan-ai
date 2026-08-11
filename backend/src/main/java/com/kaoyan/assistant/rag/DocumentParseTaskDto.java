package com.kaoyan.assistant.rag;

public record DocumentParseTaskDto(
        Long id,
        String fileSha256,
        String originalFilename,
        String contentType,
        Long fileSize,
        String parserType,
        String parserVersion,
        String status,
        String documentType,
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
