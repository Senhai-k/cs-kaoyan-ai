package com.kaoyan.assistant.school;

public record SourceInfo(
        Long id,
        String title,
        String sourceType,
        String sourceUrl,
        Integer year,
        boolean official,
        String auditStatus,
        String updatedAt
) {
}
