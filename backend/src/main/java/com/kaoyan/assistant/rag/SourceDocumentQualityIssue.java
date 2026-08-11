package com.kaoyan.assistant.rag;

public record SourceDocumentQualityIssue(
        int index,
        String level,
        String field,
        String message
) {
}
