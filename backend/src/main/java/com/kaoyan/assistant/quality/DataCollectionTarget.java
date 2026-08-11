package com.kaoyan.assistant.quality;

public record DataCollectionTarget(
        Long id,
        Long schoolId,
        String title,
        String documentType,
        Integer targetYear,
        String sourceUrl,
        String status,
        String note,
        boolean systemGenerated,
        String createdAt,
        String updatedAt
) {
}
