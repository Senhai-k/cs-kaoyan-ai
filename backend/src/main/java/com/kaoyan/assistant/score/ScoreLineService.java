package com.kaoyan.assistant.score;

import com.kaoyan.assistant.source.StructuredEvidenceValidator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScoreLineService {

    private final ScoreLineRepository repository;
    private final StructuredEvidenceValidator evidenceValidator;

    public ScoreLineService(ScoreLineRepository repository, StructuredEvidenceValidator evidenceValidator) {
        this.repository = repository;
        this.evidenceValidator = evidenceValidator;
    }

    public List<ScoreLineDto> list(Long majorId) {
        return repository.findAll(majorId);
    }

    public ScoreLineDto create(ScoreLineRequest request) {
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        Long id = repository.create(request);
        return repository.findById(id);
    }

    public ScoreLineDto update(Long id, ScoreLineRequest request) {
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        repository.update(id, request);
        return repository.findById(id);
    }

    public void delete(Long id) {
        repository.delete(id);
    }
}
