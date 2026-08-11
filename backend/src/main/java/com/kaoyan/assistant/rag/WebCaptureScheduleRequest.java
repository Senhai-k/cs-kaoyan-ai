package com.kaoyan.assistant.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WebCaptureScheduleRequest(
        @NotNull Boolean enabled,
        @NotNull @Min(6) @Max(720) Integer intervalHours
) {
}
