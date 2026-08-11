package com.kaoyan.assistant.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebCaptureChangeReviewRequest(
        @NotBlank String status,
        @NotBlank @Size(max = 500) String note
) {
}
