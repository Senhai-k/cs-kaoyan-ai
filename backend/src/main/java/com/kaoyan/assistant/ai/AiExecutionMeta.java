package com.kaoyan.assistant.ai;

import java.util.List;

public record AiExecutionMeta(
        String provider,
        String threadId,
        String status,
        double confidence,
        String route,
        int retrievalCount,
        List<String> trace
) {
}
