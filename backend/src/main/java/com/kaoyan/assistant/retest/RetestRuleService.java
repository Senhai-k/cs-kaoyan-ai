package com.kaoyan.assistant.retest;

import com.kaoyan.assistant.source.StructuredEvidenceValidator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetestRuleService {

    private final RetestRuleRepository repository;
    private final StructuredEvidenceValidator evidenceValidator;

    public RetestRuleService(RetestRuleRepository repository, StructuredEvidenceValidator evidenceValidator) {
        this.repository = repository;
        this.evidenceValidator = evidenceValidator;
    }

    public List<RetestRuleDto> list(Long majorId) {
        return repository.findAll(majorId);
    }

    public RetestRuleDto create(RetestRuleRequest request) {
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        validateScope(request);
        validateWeights(request);
        Long id = repository.create(request);
        return repository.findById(id);
    }

    public RetestRuleDto update(Long id, RetestRuleRequest request) {
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        validateScope(request);
        validateWeights(request);
        repository.update(id, request);
        return repository.findById(id);
    }

    public void delete(Long id) {
        repository.delete(id);
    }

    private void validateWeights(RetestRuleRequest request) {
        if (request.initialScoreWeight() != null && request.retestScoreWeight() != null
                && request.initialScoreWeight() + request.retestScoreWeight() != 100) {
            throw new IllegalArgumentException("初试权重和复试权重必须合计 100");
        }
    }

    private void validateScope(RetestRuleRequest request) {
        if (request.majorId() != null && request.collegeId() == null) {
            throw new IllegalArgumentException("专业级复试规则必须提供所属学院");
        }
        if (!repository.scopeBelongsToSchool(request.schoolId(), request.collegeId(), request.majorId())) {
            throw new IllegalArgumentException("复试规则的学校、学院和专业归属不一致");
        }
    }
}
