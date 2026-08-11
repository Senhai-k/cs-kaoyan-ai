package com.kaoyan.assistant.rag;

public record SourceDocumentRollbackResult(
        SourceDocumentDto document,
        Integer restoredVersionNo,
        Integer createdVersionNo,
        Integer chunkCount
) {
}
