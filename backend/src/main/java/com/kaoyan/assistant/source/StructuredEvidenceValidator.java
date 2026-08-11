package com.kaoyan.assistant.source;

import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class StructuredEvidenceValidator {

    private final DocumentSourceRepository sourceRepository;

    public StructuredEvidenceValidator(DocumentSourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    public void validate(Long schoolId, Long sourceId) {
        if (sourceId == null) {
            throw new IllegalArgumentException("结构化数据必须关联官方证据来源");
        }
        DocumentSourceDto source = sourceRepository.findById(sourceId);
        if (source == null) {
            throw new IllegalArgumentException("证据来源不存在");
        }
        if (!source.official() || !"PUBLISHED".equals(source.auditStatus())) {
            throw new IllegalArgumentException("证据来源必须是已发布的官方来源");
        }
        if (!Objects.equals(schoolId, source.schoolId())) {
            throw new IllegalArgumentException("证据来源与所选学校不一致");
        }
    }
}
