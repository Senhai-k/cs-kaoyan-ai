package com.kaoyan.assistant.quality;

public record DataCollectionTaskHistory(
        Long id,
        Long schoolId,
        String action,
        String fromStatus,
        String toStatus,
        String operator,
        String detail,
        String createdAt
) {
}
