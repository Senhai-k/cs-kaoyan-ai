package com.kaoyan.assistant.rag;

public record WebCaptureScheduleDto(
        Long targetId,
        String targetTitle,
        String sourceUrl,
        boolean enabled,
        int intervalHours,
        String nextRunAt,
        String leaseUntil,
        String lastStartedAt,
        String lastFinishedAt,
        String lastStatus,
        String lastError,
        int consecutiveFailures,
        String updatedBy,
        String updatedAt
) {
}
