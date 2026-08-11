package com.kaoyan.assistant.ai;

import com.kaoyan.assistant.rag.SourceDocumentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record InternalAgentEvidenceRequest(
        @NotNull Long targetId,
        @Valid @NotNull SourceDocumentRequest document,
        String feedback
) {
}
