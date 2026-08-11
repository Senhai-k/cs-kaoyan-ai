package com.kaoyan.assistant.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "external")
public class ExternalAiProvider implements AiProvider {

    private final String endpoint;

    public ExternalAiProvider(@Value("${app.ai.external.endpoint:}") String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public AiProviderResult answer(String question) {
        String answer = "外部 AI Provider 已启用，但当前项目尚未配置实际大模型调用。请配置 app.ai.external.endpoint 后接入具体服务。";
        String sourceSummary = endpoint == null || endpoint.isBlank()
                ? "外部 AI：未配置 endpoint"
                : "外部 AI：" + endpoint;
        return new AiProviderResult(
                answer, null, sourceSummary, List.of(sourceSummary, "当前为外部 AI 预留适配层。"),
                new AiExecutionMeta("external", null, "COMPLETED", 0.0, "external", 0, List.of())
        );
    }
}
