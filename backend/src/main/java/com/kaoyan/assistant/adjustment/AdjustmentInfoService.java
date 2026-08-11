package com.kaoyan.assistant.adjustment;

import com.kaoyan.assistant.source.StructuredEvidenceValidator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdjustmentInfoService {

    private final AdjustmentInfoRepository repository;
    private final StructuredEvidenceValidator evidenceValidator;

    public AdjustmentInfoService(AdjustmentInfoRepository repository, StructuredEvidenceValidator evidenceValidator) {
        this.repository = repository;
        this.evidenceValidator = evidenceValidator;
    }

    public List<AdjustmentInfoDto> list(Long majorId) {
        return repository.findAll(majorId);
    }

    public AdjustmentInfoDto create(AdjustmentInfoRequest request) {
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        Long id = repository.create(request);
        return repository.findById(id);
    }

    public AdjustmentInfoDto update(Long id, AdjustmentInfoRequest request) {
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        repository.update(id, request);
        return repository.findById(id);
    }

    public void delete(Long id) {
        repository.delete(id);
    }
}
