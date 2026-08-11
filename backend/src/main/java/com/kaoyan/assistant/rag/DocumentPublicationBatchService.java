package com.kaoyan.assistant.rag;

import com.kaoyan.assistant.quality.EvidenceChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DocumentPublicationBatchService {

    private final SourceDocumentRepository documentRepository;
    private final SourceDocumentVersionRepository versionRepository;
    private final SourceDocumentService documentService;
    private final DocumentPublicationBatchRepository batchRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentPublicationBatchService(SourceDocumentRepository documentRepository,
                                           SourceDocumentVersionRepository versionRepository,
                                           SourceDocumentService documentService,
                                           DocumentPublicationBatchRepository batchRepository,
                                           ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.documentService = documentService;
        this.batchRepository = batchRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<DocumentPublicationBatchDto> batches(int limit) {
        return batchRepository.findRecent(limit);
    }

    @Transactional
    public DocumentPublicationBatchResult publish(DocumentPublicationBatchRequest request, String operator) {
        List<Long> documentIds = normalizeIds(request.documentIds());
        List<SourceDocumentDto> documents = loadDocuments(documentIds);
        documents.forEach(this::validatePublishable);

        Long batchId = batchRepository.create(documents.size(), request.reason(), operator);
        Set<Long> changedSchoolIds = new LinkedHashSet<>();
        int chunkCount = 0;
        for (SourceDocumentDto document : documents) {
            ensureBaseline(document, operator);
            int previousVersionNo = versionRepository.latestVersionNo(document.id());
            documentRepository.update(document.id(), publishedRequest(document));
            SourceDocumentDto published = documentRepository.findById(document.id());
            chunkCount += documentService.generateChunks(document.id()).size();
            SourceDocumentVersionDto publishedVersion = versionRepository.snapshot(published, "PUBLISH", operator);
            batchRepository.addItem(
                    batchId, document.id(), previousVersionNo, publishedVersion.versionNo(), document.auditStatus()
            );
            addSchool(changedSchoolIds, document.schoolId());
            addSchool(changedSchoolIds, published.schoolId());
        }
        batchRepository.complete(batchId, chunkCount);
        publishChanges(changedSchoolIds);
        return new DocumentPublicationBatchResult(batchRepository.findById(batchId), documentIds);
    }

    @Transactional
    public DocumentPublicationBatchResult rollback(Long batchId, DocumentPublicationRollbackRequest request,
                                                   String operator) {
        DocumentPublicationBatchDto batch = batchRepository.findByIdForUpdate(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("发布批次不存在");
        }
        if (!"PUBLISHED".equals(batch.status())) {
            throw new IllegalArgumentException("只有已发布且未回滚的批次可以回滚");
        }
        List<DocumentPublicationBatchRepository.BatchItem> items = batchRepository.findItems(batchId);
        if (items.size() != batch.documentCount()) {
            throw new IllegalStateException("发布批次明细不完整");
        }

        List<RollbackTarget> targets = new ArrayList<>();
        for (DocumentPublicationBatchRepository.BatchItem item : items) {
            SourceDocumentDto current = documentRepository.findByIdForUpdate(item.documentId());
            if (current == null) {
                throw new IllegalArgumentException("批次资料已被删除，不能自动回滚: " + item.documentId());
            }
            Integer latestVersionNo = versionRepository.latestVersionNo(item.documentId());
            if (!item.publishedVersionNo().equals(latestVersionNo)) {
                throw new IllegalArgumentException("批次资料存在发布后的修改，不能覆盖: " + item.documentId());
            }
            SourceDocumentVersionDto previous = versionRepository.find(
                    item.documentId(), item.previousVersionNo()
            );
            if (previous == null) {
                throw new IllegalStateException("发布前版本不存在: " + item.documentId());
            }
            targets.add(new RollbackTarget(item, current, previous));
        }

        Set<Long> changedSchoolIds = new LinkedHashSet<>();
        int chunkCount = 0;
        List<Long> documentIds = new ArrayList<>();
        for (RollbackTarget target : targets) {
            documentRepository.update(target.current().id(), target.previous().toRequest());
            SourceDocumentDto restored = documentRepository.findById(target.current().id());
            chunkCount += documentService.generateChunks(restored.id()).size();
            SourceDocumentVersionDto rollbackVersion = versionRepository.snapshot(
                    restored, "BATCH_ROLLBACK", operator
            );
            batchRepository.markItemRolledBack(target.item().id(), rollbackVersion.versionNo());
            documentIds.add(restored.id());
            addSchool(changedSchoolIds, target.current().schoolId());
            addSchool(changedSchoolIds, restored.schoolId());
        }
        batchRepository.markRolledBack(batchId, request.reason(), operator, chunkCount);
        publishChanges(changedSchoolIds);
        return new DocumentPublicationBatchResult(batchRepository.findById(batchId), documentIds);
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("至少选择一份待发布资料");
        }
        if (ids.size() > 100) {
            throw new IllegalArgumentException("单批最多发布 100 份资料");
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("资料 ID 无效");
            }
            if (!uniqueIds.add(id)) {
                throw new IllegalArgumentException("发布批次包含重复资料: " + id);
            }
        }
        return List.copyOf(uniqueIds);
    }

    private List<SourceDocumentDto> loadDocuments(List<Long> ids) {
        List<SourceDocumentDto> documents = new ArrayList<>();
        for (Long id : ids) {
            SourceDocumentDto document = documentRepository.findByIdForUpdate(id);
            if (document == null) {
                throw new IllegalArgumentException("资料不存在: " + id);
            }
            documents.add(document);
        }
        return documents;
    }

    private void validatePublishable(SourceDocumentDto document) {
        String prefix = "资料 #" + document.id() + " ";
        if ("PUBLISHED".equalsIgnoreCase(document.auditStatus())) {
            throw new IllegalArgumentException(prefix + "已经发布");
        }
        if (!("DRAFT".equalsIgnoreCase(document.auditStatus())
                || "PENDING".equalsIgnoreCase(document.auditStatus()))) {
            throw new IllegalArgumentException(prefix + "状态不允许发布");
        }
        if (document.title() == null || document.title().isBlank()
                || document.documentType() == null || document.documentType().isBlank()) {
            throw new IllegalArgumentException(prefix + "缺少标题或资料类型");
        }
        if (document.schoolId() == null || document.year() == null) {
            throw new IllegalArgumentException(prefix + "缺少学校或年份");
        }
        if (document.rawText() == null || document.rawText().trim().length() < 30) {
            throw new IllegalArgumentException(prefix + "正文不足 30 字");
        }
        if (!("OFFICIAL".equalsIgnoreCase(document.sourceReliability())
                || "VERIFIED".equalsIgnoreCase(document.sourceReliability()))) {
            throw new IllegalArgumentException(prefix + "来源可信度必须为 OFFICIAL 或 VERIFIED");
        }
        validateHttpsUrl(prefix, document.sourceUrl());
    }

    private void validateHttpsUrl(String prefix, String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl == null ? "" : sourceUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException(prefix + "必须提供 HTTPS 官方来源");
            }
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith(prefix)) {
                throw ex;
            }
            throw new IllegalArgumentException(prefix + "官方来源 URL 格式无效");
        }
    }

    private void ensureBaseline(SourceDocumentDto document, String operator) {
        if (!versionRepository.hasVersions(document.id())) {
            versionRepository.snapshot(document, "BASELINE", operator);
        }
    }

    private SourceDocumentRequest publishedRequest(SourceDocumentDto document) {
        return new SourceDocumentRequest(
                document.title(), document.documentType(), document.sourceUrl(), document.schoolId(),
                document.collegeId(), document.majorId(), document.year(), "PUBLISHED",
                document.sourceReliability(), document.rawText(), document.remark()
        );
    }

    private void addSchool(Set<Long> schoolIds, Long schoolId) {
        if (schoolId != null) {
            schoolIds.add(schoolId);
        }
    }

    private void publishChanges(Set<Long> schoolIds) {
        schoolIds.forEach(schoolId -> eventPublisher.publishEvent(new EvidenceChangedEvent(schoolId)));
    }

    private record RollbackTarget(
            DocumentPublicationBatchRepository.BatchItem item,
            SourceDocumentDto current,
            SourceDocumentVersionDto previous
    ) {
    }
}
