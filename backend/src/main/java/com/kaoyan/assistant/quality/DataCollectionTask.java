package com.kaoyan.assistant.quality;

import java.util.List;

public record DataCollectionTask(
        Long schoolId,
        String schoolName,
        String schoolLevel,
        String priority,
        int priorityScore,
        int coveragePercent,
        List<Integer> targetYears,
        List<String> missingDimensions,
        List<String> recommendedDocumentTypes,
        String reason,
        String status,
        String assignee,
        String dueDate,
        String completionCriteria,
        boolean overdue,
        String officialEntryUrl,
        List<DataCollectionTarget> targets,
        List<DataCollectionTaskHistory> history,
        String createdAt,
        String updatedAt,
        String completedAt
) {
}
