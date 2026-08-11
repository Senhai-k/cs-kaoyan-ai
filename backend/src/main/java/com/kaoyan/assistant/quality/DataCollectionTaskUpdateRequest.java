package com.kaoyan.assistant.quality;

import jakarta.validation.constraints.Size;

public record DataCollectionTaskUpdateRequest(
        String status,
        @Size(max = 100) String assignee,
        String dueDate,
        @Size(min = 10, max = 1000) String completionCriteria
) {
}
