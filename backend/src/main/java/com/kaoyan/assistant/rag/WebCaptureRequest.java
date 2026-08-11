package com.kaoyan.assistant.rag;

import jakarta.validation.constraints.NotNull;

public record WebCaptureRequest(@NotNull Long targetId) {
}
