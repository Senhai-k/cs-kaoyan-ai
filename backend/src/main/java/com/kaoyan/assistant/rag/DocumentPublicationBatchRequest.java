package com.kaoyan.assistant.rag;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DocumentPublicationBatchRequest(
        @NotEmpty @Size(max = 100) List<Long> documentIds,
        @NotBlank @Size(max = 500) String reason
) {
}
