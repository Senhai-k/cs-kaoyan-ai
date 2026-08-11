package com.kaoyan.assistant.source;

import jakarta.validation.constraints.NotBlank;

public record DocumentSourceRequest(
        @NotBlank String title,
        String sourceType,
        String sourceUrl,
        String publishDate,
        Long schoolId,
        Long collegeId,
        Integer year,
        boolean official,
        String auditStatus,
        String remark
) {
}
