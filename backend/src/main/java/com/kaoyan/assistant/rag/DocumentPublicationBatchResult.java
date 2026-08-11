package com.kaoyan.assistant.rag;

import java.util.List;

public record DocumentPublicationBatchResult(
        DocumentPublicationBatchDto batch,
        List<Long> documentIds
) {
}
