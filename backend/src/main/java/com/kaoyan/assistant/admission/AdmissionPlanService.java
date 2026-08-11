package com.kaoyan.assistant.admission;

import com.kaoyan.assistant.source.StructuredEvidenceValidator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdmissionPlanService {

    private final AdmissionPlanRepository repository;
    private final StructuredEvidenceValidator evidenceValidator;

    public AdmissionPlanService(AdmissionPlanRepository repository, StructuredEvidenceValidator evidenceValidator) {
        this.repository = repository;
        this.evidenceValidator = evidenceValidator;
    }

    public List<AdmissionPlanDto> list(Long majorId) {
        return repository.findAll(majorId);
    }

    public AdmissionPlanDto create(AdmissionPlanRequest request) {
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        Long id = repository.create(request);
        return repository.findById(id);
    }

    public AdmissionPlanDto update(Long id, AdmissionPlanRequest request) {
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        repository.update(id, request);
        return repository.findById(id);
    }

    public void delete(Long id) {
        repository.delete(id);
    }
}
