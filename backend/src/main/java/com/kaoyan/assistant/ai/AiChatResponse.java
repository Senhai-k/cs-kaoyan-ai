package com.kaoyan.assistant.ai;

import java.util.List;

public record AiChatResponse(String answer, List<String> sources, AiExecutionMeta meta) {
}
