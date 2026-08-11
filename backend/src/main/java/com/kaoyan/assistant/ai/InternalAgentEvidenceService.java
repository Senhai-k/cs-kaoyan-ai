package com.kaoyan.assistant.ai;

import com.kaoyan.assistant.quality.DataCollectionTarget;
import com.kaoyan.assistant.quality.DataCoverageService;
import com.kaoyan.assistant.rag.SourceDocumentDto;
import com.kaoyan.assistant.rag.SourceDocumentQualityReport;
import com.kaoyan.assistant.rag.SourceDocumentRepository;
import com.kaoyan.assistant.rag.SourceDocumentRequest;
import com.kaoyan.assistant.rag.SourceDocumentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class InternalAgentEvidenceService {

    private final SourceDocumentRepository documentRepository;
    private final SourceDocumentService documentService;
    private final DataCoverageService dataCoverageService;

    public InternalAgentEvidenceService(SourceDocumentRepository documentRepository,
                                        SourceDocumentService documentService,
                                        DataCoverageService dataCoverageService) {
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.dataCoverageService = dataCoverageService;
    }

    @Transactional
    public InternalAgentEvidenceResult publish(InternalAgentEvidenceRequest request) {
        SourceDocumentRequest document = request.document();
        if (!"PUBLISHED".equalsIgnoreCase(document.auditStatus())
                || !"OFFICIAL".equalsIgnoreCase(document.sourceReliability())) {
            throw new IllegalArgumentException("agent evidence must be PUBLISHED and OFFICIAL");
        }
        SourceDocumentQualityReport quality = documentService.qualityCheck(List.of(document));
        if (!quality.importable()) {
            throw new IllegalArgumentException("agent evidence quality check failed");
        }

        DataCollectionTarget target = dataCoverageService.verifyAgentTarget(
                request.targetId(), document.schoolId(), document.sourceUrl(),
                document.documentType(), document.year(), request.feedback()
        );

        SourceDocumentDto existing = documentRepository.findBySourceUrl(document.sourceUrl());
        if (existing != null && !Objects.equals(existing.schoolId(), document.schoolId())) {
            throw new IllegalArgumentException("existing source document belongs to another school");
        }
        boolean created = existing == null;
        SourceDocumentDto saved = created
                ? documentService.create(document, "coverage-workflow")
                : documentService.update(existing.id(), document, "coverage-workflow");
        if (saved == null) {
            throw new IllegalStateException("agent evidence could not be persisted");
        }
        int chunkCount = documentService.generateChunks(saved.id()).size();
        return new InternalAgentEvidenceResult(
                saved.id(), chunkCount, created, target.id(), target.status()
        );
    }
}
