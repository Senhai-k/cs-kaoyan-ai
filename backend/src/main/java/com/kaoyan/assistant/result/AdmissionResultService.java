package com.kaoyan.assistant.result;

import com.kaoyan.assistant.source.StructuredEvidenceValidator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdmissionResultService {

    private final AdmissionResultRepository repository;
    private final StructuredEvidenceValidator evidenceValidator;

    public AdmissionResultService(AdmissionResultRepository repository, StructuredEvidenceValidator evidenceValidator) {
        this.repository = repository;
        this.evidenceValidator = evidenceValidator;
    }

    public List<AdmissionResultDto> list(Long majorId) {
        return repository.findAll(majorId);
    }

    public AdmissionResultDto create(AdmissionResultRequest request) {
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        Long id = repository.create(request);
        return repository.findById(id);
    }

    public AdmissionResultDto update(Long id, AdmissionResultRequest request) {
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        repository.update(id, request);
        return repository.findById(id);
    }

    public void delete(Long id) {
        repository.delete(id);
    }
}
