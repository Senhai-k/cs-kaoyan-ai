package com.kaoyan.assistant.quality;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DataCollectionTargetRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 50) String documentType,
        Integer targetYear,
        @NotBlank @Size(max = 500) String sourceUrl,
        String status,
        @Size(max = 500) String note
) {
}
