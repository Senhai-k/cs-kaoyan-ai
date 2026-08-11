package com.kaoyan.assistant.source;

import com.kaoyan.assistant.quality.EvidenceChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentSourceService {

    private final DocumentSourceRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentSourceService(DocumentSourceRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public List<DocumentSourceDto> list(Long schoolId, String auditStatus) {
        return repository.findAll(schoolId, auditStatus);
    }

    public DocumentSourceDto create(DocumentSourceRequest request) {
        Long id = repository.create(request);
        DocumentSourceDto result = repository.findById(id);
        publishChange(result == null ? null : result.schoolId());
        return result;
    }

    public DocumentSourceDto update(Long id, DocumentSourceRequest request) {
        DocumentSourceDto previous = repository.findById(id);
        repository.update(id, request);
        DocumentSourceDto result = repository.findById(id);
        publishChange(previous == null ? null : previous.schoolId());
        publishChange(result == null ? null : result.schoolId());
        return result;
    }

    public void delete(Long id) {
        DocumentSourceDto previous = repository.findById(id);
        repository.delete(id);
        publishChange(previous == null ? null : previous.schoolId());
    }

    private void publishChange(Long schoolId) {
        if (schoolId != null) {
            eventPublisher.publishEvent(new EvidenceChangedEvent(schoolId));
        }
    }
}
