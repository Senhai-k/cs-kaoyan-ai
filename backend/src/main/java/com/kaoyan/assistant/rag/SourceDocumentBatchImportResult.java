package com.kaoyan.assistant.rag;

import java.util.List;

public record SourceDocumentBatchImportResult(
        int importedCount,
        int chunkCount,
        List<Long> documentIds
) {
}
