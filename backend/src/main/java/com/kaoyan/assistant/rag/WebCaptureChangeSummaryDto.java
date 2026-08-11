package com.kaoyan.assistant.rag;

public record WebCaptureChangeSummaryDto(
        long totalCount,
        long pendingCount,
        long acknowledgedCount,
        long ignoredCount,
        double averageChangeRatio,
        double maxChangeRatio,
        String oldestPendingAt,
        long oldestPendingAgeSeconds
) {
}
