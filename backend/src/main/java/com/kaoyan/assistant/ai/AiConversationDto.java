package com.kaoyan.assistant.ai;

public record AiConversationDto(
        Long id,
        String question,
        String answer,
        Long relatedSchoolId,
        Long relatedMajorId,
        String sourceSummary,
        String createdAt
) {
}
