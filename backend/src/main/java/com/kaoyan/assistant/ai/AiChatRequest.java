package com.kaoyan.assistant.ai;

import jakarta.validation.constraints.NotBlank;

public record AiChatRequest(@NotBlank String question) {
}
