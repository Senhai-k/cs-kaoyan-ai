package com.kaoyan.assistant.book;

import com.kaoyan.assistant.source.StructuredEvidenceValidator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReferenceBookService {

    private final ReferenceBookRepository repository;
    private final StructuredEvidenceValidator evidenceValidator;

    public ReferenceBookService(ReferenceBookRepository repository, StructuredEvidenceValidator evidenceValidator) {
        this.repository = repository;
        this.evidenceValidator = evidenceValidator;
    }

    public List<ReferenceBookDto> list(Long majorId) {
        return repository.findAll(majorId);
    }

    public ReferenceBookDto create(ReferenceBookRequest request) {
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        Long id = repository.create(request);
        return repository.findById(id);
    }

    public ReferenceBookDto update(Long id, ReferenceBookRequest request) {
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        repository.update(id, request);
        return repository.findById(id);
    }

    public void delete(Long id) {
        repository.delete(id);
    }
}
