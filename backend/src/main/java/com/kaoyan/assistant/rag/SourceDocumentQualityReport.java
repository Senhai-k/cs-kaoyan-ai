package com.kaoyan.assistant.rag;

import java.util.List;

public record SourceDocumentQualityReport(
        int totalCount,
        int errorCount,
        int warningCount,
        boolean importable,
        boolean publishable,
        List<SourceDocumentQualityIssue> issues
) {
}
