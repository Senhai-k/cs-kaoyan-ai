package com.kaoyan.assistant.quality;

public record DataCoverageDimension(
        String key,
        String label,
        int coveredSchoolCount,
        int totalSchoolCount,
        int coveragePercent
) {
}
