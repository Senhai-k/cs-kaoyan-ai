package com.kaoyan.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaoyan.assistant.school.SchoolRepository;
import com.kaoyan.assistant.schoolscore.SelfScoreLineImportRequest;
import com.kaoyan.assistant.schoolscore.SelfScoreLineImportResult;
import com.kaoyan.assistant.schoolscore.SelfScoreLineImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class SelfScoreLineImportServiceTests {

    @Autowired
    private SelfScoreLineImportService service;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SelfScoreLineImportRequest request;

    @BeforeEach
    void loadReviewedBatchAndSchools() throws IOException {
        Path batchPath = Path.of(System.getProperty("user.dir"), "..", "database",
                "self-score-lines-2026-reviewed.json").normalize();
        request = objectMapper.readValue(Files.readString(batchPath), SelfScoreLineImportRequest.class);
        request.records().forEach(line -> jdbcTemplate.update("""
                MERGE INTO school (name, province, region, school_level, is_self_determined_score)
                KEY(name) VALUES (?, '待核验', '待核验', '自主划线', 1)
                """, line.schoolName()));
    }

    @Test
    void importsReviewedBatchIdempotentlyAndKeepsLineLevelsSeparate() {
        SelfScoreLineImportResult first = service.importBatch(request);
        SelfScoreLineImportResult second = service.importBatch(request);

        assertEquals(34, first.inputRecords());
        assertEquals(33, first.available());
        assertEquals(1, first.unavailable());
        assertEquals(34, first.scoreLinesCreated());
        assertEquals(0, second.scoreLinesCreated());
        assertEquals(34, second.existingRecords());
        assertEquals(34, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM school_score_line WHERE year = 2026", Integer.class));
        assertEquals(34, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM source_document WHERE document_type = '学校基本线' AND year = 2026",
                Integer.class));
        assertEquals(34, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunk WHERE document_type = '学校基本线' AND year = 2026",
                Integer.class));
        assertEquals("CHSI_SELF_SCORE_LINE", jdbcTemplate.queryForObject("""
                SELECT catalog_type FROM catalog_import_batch WHERE batch_sha256 = ?
                """, String.class, request.batchSha256()));

        Long pekingId = jdbcTemplate.queryForObject("SELECT id FROM school WHERE name = '北京大学'", Long.class);
        var pekingDetail = schoolRepository.findDetailById(pekingId);
        assertEquals(300, pekingDetail.schoolScoreLines().get(0).totalScore());
        assertEquals(55, pekingDetail.schoolScoreLines().get(0).politicsScore());
        assertNotNull(pekingDetail.schoolScoreLines().get(0).sourceId());

        Long wuhanId = jdbcTemplate.queryForObject("SELECT id FROM school WHERE name = '武汉大学'", Long.class);
        var wuhanDetail = schoolRepository.findDetailById(wuhanId);
        assertEquals("NOT_PUBLISHED", wuhanDetail.schoolScoreLines().get(0).availabilityStatus());
        assertEquals(null, wuhanDetail.schoolScoreLines().get(0).totalScore());
        assertTrue(wuhanDetail.schoolScoreLines().get(0).scopeNote().contains("尚未填写"));
    }

    @Test
    void rejectsUnavailableRecordWithFabricatedScore() {
        SelfScoreLineImportRequest.ReviewedLine wuhan = request.records().stream()
                .filter(line -> "武汉大学".equals(line.schoolName())).findFirst().orElseThrow();
        SelfScoreLineImportRequest.ReviewedLine invalid = new SelfScoreLineImportRequest.ReviewedLine(
                wuhan.schoolName(), wuhan.province(), wuhan.city(), wuhan.schoolLevel(), wuhan.is985(), wuhan.is211(),
                wuhan.isDoubleFirstClass(), wuhan.title(), wuhan.articleUrl(), wuhan.publishedDate(),
                wuhan.articleSha256(), wuhan.imageUrl(), wuhan.imageSha256(), wuhan.categoryCode(),
                wuhan.categoryName(), wuhan.degreeType(), 300, null, null, null, null, null, null,
                wuhan.availabilityStatus(), wuhan.scopeNote(), wuhan.remark());
        var records = request.records().stream().map(line -> line == wuhan ? invalid : line).toList();
        SelfScoreLineImportRequest invalidRequest = new SelfScoreLineImportRequest(
                request.schemaVersion(), request.year(), request.publisher(), request.portalUrl(),
                request.retrievedAt(), request.reviewedAt(), request.sourceBatchSha256(), request.batchSha256(),
                request.stats(), records);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.importBatch(invalidRequest));
        assertTrue(error.getMessage().contains("不能包含推断分数"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM school_score_line WHERE year = 2026", Integer.class));
    }
}
