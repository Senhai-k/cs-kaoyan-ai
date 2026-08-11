package com.kaoyan.assistant.ai;

import java.util.List;

public record AiProviderResult(
        String answer,
        Long relatedSchoolId,
        String sourceSummary,
        List<String> sources,
        AiExecutionMeta meta
) {
}
