package com.kaoyan.assistant.exam;

import com.kaoyan.assistant.source.StructuredEvidenceValidator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExamSubjectService {

    private final ExamSubjectRepository repository;
    private final StructuredEvidenceValidator evidenceValidator;

    public ExamSubjectService(ExamSubjectRepository repository, StructuredEvidenceValidator evidenceValidator) {
        this.repository = repository;
        this.evidenceValidator = evidenceValidator;
    }

    public List<ExamSubjectDto> list(Long majorId) {
        return repository.findAll(majorId);
    }

    public ExamSubjectDto create(ExamSubjectRequest request) {
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        Long id = repository.create(request);
        return repository.findById(id);
    }

    public ExamSubjectDto update(Long id, ExamSubjectRequest request) {
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        repository.update(id, request);
        return repository.findById(id);
    }

    public void delete(Long id) {
        repository.delete(id);
    }
}
