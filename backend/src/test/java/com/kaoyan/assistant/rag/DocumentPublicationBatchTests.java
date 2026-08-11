package com.kaoyan.assistant.rag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class DocumentPublicationBatchTests {

    @Autowired
    private SourceDocumentService documentService;

    @Autowired
    private DocumentPublicationBatchService batchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    @Rollback
    void publishesMultipleDocumentsInOneAuditedBatch() {
        SourceDocumentDto first = createDraft("DRAFT", "OFFICIAL", "第一份");
        SourceDocumentDto second = createDraft("PENDING", "VERIFIED", "第二份");

        DocumentPublicationBatchResult result = batchService.publish(
                new DocumentPublicationBatchRequest(List.of(first.id(), second.id()), "完成双人复核"),
                "publication-admin"
        );

        assertThat(result.batch().status()).isEqualTo("PUBLISHED");
        assertThat(result.batch().documentCount()).isEqualTo(2);
        assertThat(result.batch().chunkCount()).isPositive();
        assertThat(result.documentIds()).containsExactly(first.id(), second.id());
        assertThat(documentService.detail(first.id()).auditStatus()).isEqualTo("PUBLISHED");
        assertThat(documentService.detail(second.id()).auditStatus()).isEqualTo("PUBLISHED");
        assertThat(documentService.chunks(first.id())).allSatisfy(
                chunk -> assertThat(chunk.auditStatus()).isEqualTo("PUBLISHED")
        );
        assertThat(documentService.versions(first.id())).extracting(SourceDocumentVersionDto::operation)
                .containsExactly("PUBLISH", "CREATE");
    }

    @Test
    @Transactional
    @Rollback
    void invalidDocumentRollsBackWholePublicationBeforeWritingBatch() {
        SourceDocumentDto valid = createDraft("DRAFT", "OFFICIAL", "有效资料");
        SourceDocumentDto invalid = createDraft("DRAFT", "UNKNOWN", "未核验资料");
        Integer batchesBefore = count("document_publication_batch");

        assertThatThrownBy(() -> batchService.publish(
                new DocumentPublicationBatchRequest(List.of(valid.id(), invalid.id()), "准备发布"),
                "publication-admin"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("来源可信度");

        assertThat(documentService.detail(valid.id()).auditStatus()).isEqualTo("DRAFT");
        assertThat(documentService.detail(invalid.id()).auditStatus()).isEqualTo("DRAFT");
        assertThat(count("document_publication_batch")).isEqualTo(batchesBefore);
    }

    @Test
    @Transactional
    @Rollback
    void rollsBackWholeBatchToPrePublicationVersions() {
        SourceDocumentDto first = createDraft("DRAFT", "OFFICIAL", "回滚一");
        SourceDocumentDto second = createDraft("PENDING", "OFFICIAL", "回滚二");
        DocumentPublicationBatchResult published = batchService.publish(
                new DocumentPublicationBatchRequest(List.of(first.id(), second.id()), "发布前复核完成"),
                "publication-admin"
        );

        DocumentPublicationBatchResult rolledBack = batchService.rollback(
                published.batch().id(), new DocumentPublicationRollbackRequest("发现上游公告撤回"),
                "rollback-admin"
        );

        assertThat(rolledBack.batch().status()).isEqualTo("ROLLED_BACK");
        assertThat(rolledBack.batch().rollbackChunkCount()).isPositive();
        assertThat(rolledBack.batch().rollbackOperator()).isEqualTo("rollback-admin");
        assertThat(documentService.detail(first.id()).auditStatus()).isEqualTo("DRAFT");
        assertThat(documentService.detail(second.id()).auditStatus()).isEqualTo("PENDING");
        assertThat(documentService.chunks(first.id())).allSatisfy(
                chunk -> assertThat(chunk.auditStatus()).isEqualTo("DRAFT")
        );
        assertThat(documentService.versions(first.id())).extracting(SourceDocumentVersionDto::operation)
                .containsExactly("BATCH_ROLLBACK", "PUBLISH", "CREATE");
    }

    @Test
    @Transactional
    @Rollback
    void rejectsBatchRollbackAfterAnyDocumentHasNewerEdit() {
        SourceDocumentDto draft = createDraft("DRAFT", "OFFICIAL", "后续修改");
        DocumentPublicationBatchResult published = batchService.publish(
                new DocumentPublicationBatchRequest(List.of(draft.id()), "首次发布"),
                "publication-admin"
        );
        SourceDocumentDto current = documentService.detail(draft.id());
        documentService.update(draft.id(), new SourceDocumentRequest(
                "发布后的修订", current.documentType(), current.sourceUrl(), current.schoolId(),
                current.collegeId(), current.majorId(), current.year(), "PUBLISHED",
                current.sourceReliability(), current.rawText() + "补充修订内容。", current.remark()
        ), "editor-after-publish");

        assertThatThrownBy(() -> batchService.rollback(
                published.batch().id(), new DocumentPublicationRollbackRequest("尝试回滚"),
                "rollback-admin"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("发布后的修改");

        assertThat(documentService.detail(draft.id()).title()).isEqualTo("发布后的修订");
        assertThat(batchService.batches(20)).filteredOn(batch -> batch.id().equals(published.batch().id()))
                .singleElement().extracting(DocumentPublicationBatchDto::status).isEqualTo("PUBLISHED");
    }

    private SourceDocumentDto createDraft(String status, String reliability, String title) {
        return documentService.create(new SourceDocumentRequest(
                title, "招生专业目录",
                "https://cs.test.edu.cn/publication/" + UUID.randomUUID() + "/article.htm",
                2L, 2L, 2L, 2026, status, reliability,
                (title + "测试大学2026年计算机专业招生资料正文，包含可核验的完整官方信息。").repeat(8),
                "批次发布测试"
        ), "draft-editor");
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
