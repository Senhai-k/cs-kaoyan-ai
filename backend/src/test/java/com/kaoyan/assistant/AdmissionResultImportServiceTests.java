package com.kaoyan.assistant;

import com.kaoyan.assistant.resultimport.AdmissionResultImportDraft;
import com.kaoyan.assistant.resultimport.AdmissionResultImportPreview;
import com.kaoyan.assistant.resultimport.AdmissionResultImportPublishResult;
import com.kaoyan.assistant.resultimport.AdmissionResultImportRequest;
import com.kaoyan.assistant.resultimport.AdmissionResultImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AdmissionResultImportServiceTests {

    private static final String SOURCE_SHA = "1".repeat(64);
    private static final String BATCH_SHA = "2".repeat(64);

    @Autowired
    private AdmissionResultImportService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long sourceId;

    @BeforeEach
    void createOfficialAdmissionListSource() {
        jdbcTemplate.update("""
                INSERT INTO document_source (title, source_type, source_url, publish_date, school_id,
                  college_id, year, is_official, audit_status, remark)
                VALUES ('北京大学2026年硕士研究生拟录取名单', '拟录取名单',
                  'https://admission-test.pku.edu.cn/2026/list', '2026-04-20', 1, 1, 2026, 1,
                  'PUBLISHED', '测试事务内官方名单')
                """);
        sourceId = jdbcTemplate.queryForObject("""
                SELECT id FROM document_source WHERE source_url = 'https://admission-test.pku.edu.cn/2026/list'
                """, Long.class);
    }

    @Test
    void createsDraftAndAggregatesScoresWithoutWritingPublishedResult() {
        AdmissionResultImportDraft draft = service.createDraft(request(BATCH_SHA, records(335, 360, 390)));

        assertFalse(draft.existing());
        assertEquals(3, draft.preview().inputRecords());
        assertEquals(1, draft.preview().groupCount());
        assertEquals(1, draft.preview().mappedGroupCount());
        assertTrue(draft.preview().publishable());
        AdmissionResultImportPreview.GroupPreview group = draft.preview().groups().get(0);
        assertEquals(3, group.admittedCount());
        assertEquals(335, group.lowestScore());
        assertEquals(361.67, group.averageScore());
        assertEquals(390, group.highestScore());
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admission_result_candidate WHERE batch_id = ?", Integer.class,
                draft.batch().id()));
        assertEquals(0, resultCount());
    }

    @Test
    void publishesOnceAndRepeatedImportAndPublishStayIdempotent() {
        AdmissionResultImportRequest request = request(BATCH_SHA, records(335, 360, 390));
        AdmissionResultImportDraft firstDraft = service.createDraft(request);
        AdmissionResultImportDraft repeatedDraft = service.createDraft(request);
        AdmissionResultImportPublishResult firstPublish = service.publish(firstDraft.batch().id());
        AdmissionResultImportPublishResult repeatedPublish = service.publish(firstDraft.batch().id());

        assertTrue(repeatedDraft.existing());
        assertEquals(firstDraft.batch().id(), repeatedDraft.batch().id());
        assertEquals(1, firstPublish.admissionResultsCreated());
        assertEquals(0, repeatedPublish.admissionResultsCreated());
        assertEquals(1, repeatedPublish.existingResults());
        assertEquals(1, resultCount());
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT admitted_count FROM admission_result WHERE major_id = 1 AND year = 2026", Integer.class));
        assertEquals(361.67, jdbcTemplate.queryForObject(
                "SELECT average_score FROM admission_result WHERE major_id = 1 AND year = 2026", Double.class));
    }

    @Test
    void rejectsDuplicateAnonymousKeyBeforeAnyWrite() {
        List<AdmissionResultImportRequest.CandidateRecord> records = List.of(
                record("A".repeat(64), 350), record("a".repeat(64), 360));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createDraft(request(BATCH_SHA, records)));

        assertTrue(error.getMessage().contains("重复匿名候选人键"));
        assertEquals(0, batchCount());
        assertEquals(0, candidateCount());
    }

    @Test
    void rejectsWrongSourceTypeBeforeAnyWrite() {
        jdbcTemplate.update("UPDATE document_source SET title = '北京大学2026年复试录取方案', source_type = '复试方案' WHERE id = ?",
                sourceId);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createDraft(request(BATCH_SHA, records(350))));

        assertTrue(error.getMessage().contains("拟录取名单"));
        assertEquals(0, batchCount());
        assertEquals(0, candidateCount());
    }

    @Test
    void leavesUnmappedAmbiguousAndSpecialProgramGroupsUnpublishable() {
        AdmissionResultImportPreview unmapped = service.preview(request(BATCH_SHA, List.of(
                new AdmissionResultImportRequest.CandidateRecord("a".repeat(64), "不存在学院", "081200",
                        "计算机科学与技术", "学硕", "全日制", "普通计划", 350, null, null, null))));
        assertEquals("UNMAPPED", unmapped.groups().get(0).mappingStatus());
        assertFalse(unmapped.publishable());

        AdmissionResultImportPreview special = service.preview(request(BATCH_SHA, List.of(
                new AdmissionResultImportRequest.CandidateRecord("b".repeat(64), "计算机学院", "081200",
                        "计算机科学与技术", "学硕", "全日制", "少数民族骨干计划", 350, null, null, "少干计划"))));
        assertEquals("UNSUPPORTED_SCOPE", special.groups().get(0).mappingStatus());
        assertFalse(special.publishable());

        jdbcTemplate.update("""
                INSERT INTO major (school_id, college_id, name, major_code, degree_type, study_mode, remark)
                VALUES (1, 1, '计算机科学与技术重复档案', '081200', '学硕', '全日制', '测试歧义')
                """);
        AdmissionResultImportPreview ambiguous = service.preview(request(BATCH_SHA, records(350)));
        assertEquals("AMBIGUOUS", ambiguous.groups().get(0).mappingStatus());
        assertFalse(ambiguous.publishable());
    }

    @Test
    void partialInitialScoresPublishCountButKeepScoreMetricsNull() {
        List<AdmissionResultImportRequest.CandidateRecord> records = List.of(
                record("a".repeat(64), 350), record("b".repeat(64), null));
        AdmissionResultImportDraft draft = service.createDraft(request(BATCH_SHA, records));
        AdmissionResultImportPreview.GroupPreview group = draft.preview().groups().get(0);

        assertTrue(draft.preview().publishable());
        assertEquals(1, group.scoreCoverageCount());
        assertNull(group.lowestScore());
        assertNull(group.averageScore());
        assertNull(group.highestScore());

        service.publish(draft.batch().id());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT admitted_count FROM admission_result WHERE major_id = 1 AND year = 2026", Integer.class));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT lowest_score FROM admission_result WHERE major_id = 1 AND year = 2026", Integer.class));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT average_score FROM admission_result WHERE major_id = 1 AND year = 2026", Double.class));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT highest_score FROM admission_result WHERE major_id = 1 AND year = 2026", Integer.class));
    }

    @Test
    void rejectsHashReuseWithDifferentCandidateSet() {
        service.createDraft(request(BATCH_SHA, records(350)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createDraft(request(BATCH_SHA, records(350, 360))));

        assertTrue(error.getMessage().contains("候选人集合不一致"));
        assertEquals(1, batchCount());
        assertEquals(1, candidateCount());
    }

    @Test
    void doesNotOverwriteExistingAdmissionResult() {
        jdbcTemplate.update("""
                INSERT INTO admission_result (school_id, college_id, major_id, year, admitted_count, remark)
                VALUES (1, 1, 1, 2026, 9, '人工核验结果')
                """);
        AdmissionResultImportDraft draft = service.createDraft(request(BATCH_SHA, records(350)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.publish(draft.batch().id()));

        assertTrue(error.getMessage().contains("禁止自动覆盖"));
        assertEquals(9, jdbcTemplate.queryForObject(
                "SELECT admitted_count FROM admission_result WHERE major_id = 1 AND year = 2026", Integer.class));
        assertEquals("DRAFT", jdbcTemplate.queryForObject(
                "SELECT status FROM admission_result_import_batch WHERE id = ?", String.class, draft.batch().id()));
    }

    private AdmissionResultImportRequest request(String batchSha,
                                                 List<AdmissionResultImportRequest.CandidateRecord> records) {
        return new AdmissionResultImportRequest(1, 1L, 2026, "拟录取名单", sourceId,
                SOURCE_SHA, batchSha, "匿名聚合测试批次", records);
    }

    private List<AdmissionResultImportRequest.CandidateRecord> records(Integer... scores) {
        return java.util.stream.IntStream.range(0, scores.length)
                .mapToObj(index -> record(Integer.toHexString(index + 10).repeat(64).substring(0, 64), scores[index]))
                .toList();
    }

    private AdmissionResultImportRequest.CandidateRecord record(String key, Integer score) {
        return new AdmissionResultImportRequest.CandidateRecord(key, "计算机学院", "081200",
                "计算机科学与技术", "学硕", "全日制", "普通计划", score, 85.5, 81.25, null);
    }

    private int batchCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admission_result_import_batch", Integer.class);
    }

    private int candidateCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admission_result_candidate", Integer.class);
    }

    private int resultCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admission_result WHERE major_id = 1 AND year = 2026", Integer.class);
    }
}
