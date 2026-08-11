package com.kaoyan.assistant.rag;

public record WebCaptureChangeDto(
        Long id,
        Long targetId,
        Long previousTaskId,
        Long currentTaskId,
        String previousSha256,
        String currentSha256,
        Integer previousLength,
        Integer currentLength,
        Integer addedLineCount,
        Integer removedLineCount,
        Double changeRatio,
        String previousExcerpt,
        String currentExcerpt,
        String status,
        String reviewNote,
        String reviewer,
        String detectedAt,
        String reviewedAt
) {
}
