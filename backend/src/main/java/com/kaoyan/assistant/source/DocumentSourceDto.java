package com.kaoyan.assistant.source;

public record DocumentSourceDto(
        Long id,
        String title,
        String sourceType,
        String sourceUrl,
        String publishDate,
        Long schoolId,
        Long collegeId,
        Integer year,
        boolean official,
        String auditStatus,
        String updatedAt,
        String remark
) {
}
