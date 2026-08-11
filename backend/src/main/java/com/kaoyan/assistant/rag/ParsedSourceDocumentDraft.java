package com.kaoyan.assistant.rag;

public record ParsedSourceDocumentDraft(
        String title,
        String documentType,
        String rawText,
        String remark,
        Long parseTaskId,
        String fileSha256,
        boolean duplicate,
        String parserVersion
) {
}
