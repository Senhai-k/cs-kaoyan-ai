package com.kaoyan.assistant.rag;

import java.util.List;

public record WebCaptureMonitorRunResult(
        int claimedCount,
        int completedCount,
        int failedCount,
        int changesDetected,
        List<Long> targetIds
) {
}
