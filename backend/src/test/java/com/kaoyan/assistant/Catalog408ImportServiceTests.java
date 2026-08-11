package com.kaoyan.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.assistant.catalog.Catalog408ImportRequest;
import com.kaoyan.assistant.catalog.Catalog408ImportResult;
import com.kaoyan.assistant.catalog.Catalog408ImportService;
import com.kaoyan.assistant.school.SchoolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class Catalog408ImportServiceTests {

    @Autowired
    private Catalog408ImportService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SchoolRepository schoolRepository;

    @Test
    void importsOfficial408RecordIdempotently() {
        Catalog408ImportRequest request = validRequest("408", "https://yz.chsi.com.cn/zsml/zydetail.do?zydm=081200");

        Catalog408ImportResult first = service.importBatch(request);
        Catalog408ImportResult second = service.importBatch(request);

        assertEquals(1, first.examSubjectsCreated());
        assertEquals(1, first.admissionPlansCreated());
        assertEquals(0, second.examSubjectsCreated());
        assertEquals(0, second.admissionPlansCreated());
        assertEquals(1, second.existingRecords());
        assertEquals(1, service.latestStatus().inputRecords());
        assertEquals(false, service.latestStatus().complete());
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM exam_subject es
                JOIN major m ON m.id = es.major_id
                JOIN school s ON s.id = es.school_id
                WHERE s.name = '北京大学' AND m.major_code = '081200'
                  AND es.year = 2026 AND es.professional_subject = '408 计算机学科专业基础'
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admission_plan ap
                JOIN major m ON m.id = ap.major_id
                JOIN school s ON s.id = ap.school_id
                WHERE s.name = '北京大学' AND m.major_code = '081200' AND ap.year = 2026
                  AND ap.unified_quota = 19 AND ap.total_quota IS NULL
                  AND ap.recommended_quota IS NULL AND ap.source_id IS NOT NULL
                  AND ap.remark LIKE '研招网目录自动结构化｜%'
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM source_document
                WHERE title = '2026年研招网目录 - 北京大学 计算机学院 081200 全日制 101-201-301-408 408科目证据'
                """, Integer.class));
        Long schoolId = jdbcTemplate.queryForObject("SELECT id FROM school WHERE name = '北京大学'", Long.class);
        assertTrue(schoolRepository.findDetailById(schoolId).programs().stream()
                .anyMatch(program -> "081200".equals(program.majorCode())
                        && program.professionalSubject().startsWith("408 ")));
    }

    @Test
    void mergesDirectionsAndRemarksAcrossSubjectCombinations() {
        service.importBatch(validRequest("408", "https://yz.chsi.com.cn/zsml/zydetail.do?zydm=081200",
                "计算理论", "第一条目录备注", "c".repeat(64)));
        service.importBatch(validRequest("408", "https://yz.chsi.com.cn/zsml/zydetail.do?zydm=081200",
                "系统结构", "第二条目录备注", "d".repeat(64)));

        String directions = jdbcTemplate.queryForObject("""
                SELECT m.research_direction FROM major m
                JOIN school s ON s.id = m.school_id
                WHERE s.name = '北京大学' AND m.major_code = '081200'
                ORDER BY m.id LIMIT 1
                """, String.class);
        String remarks = jdbcTemplate.queryForObject("""
                SELECT m.remark FROM major m
                JOIN school s ON s.id = m.school_id
                WHERE s.name = '北京大学' AND m.major_code = '081200'
                ORDER BY m.id LIMIT 1
                """, String.class);

        assertTrue(directions.contains("计算理论"));
        assertTrue(directions.contains("系统结构"));
        assertTrue(remarks.contains("第一条目录备注"));
        assertTrue(remarks.contains("第二条目录备注"));
    }

    @Test
    void rejectsNon408AndNonChsiSourcesWithoutWriting() {
        int before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exam_subject", Integer.class);

        IllegalArgumentException non408 = assertThrows(IllegalArgumentException.class,
                () -> service.importBatch(validRequest("912", "https://yz.chsi.com.cn/zsml/zydetail.do?zydm=081200")));
        IllegalArgumentException unofficial = assertThrows(IllegalArgumentException.class,
                () -> service.importBatch(validRequest("408", "https://example.com/catalog")));

        assertTrue(non408.getMessage().contains("不是408"));
        assertTrue(unofficial.getMessage().contains("研招网"));
        assertEquals(before, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exam_subject", Integer.class));
    }

    @Test
    void skipsDirectionLevelAndConflictingQuotaTexts() {
        Catalog408ImportRequest directionLevel = withQuotaTexts(
                validRequest("408", "https://yz.chsi.com.cn/zsml/zydetail.do?zydm=081200"),
                List.of("研究方向：19(不含推免)")
        );
        Catalog408ImportRequest conflicting = withQuotaTexts(
                validRequest("408", "https://yz.chsi.com.cn/zsml/zydetail.do?zydm=081200",
                        "系统结构", "冲突计划测试", "e".repeat(64)),
                List.of("专业：19(不含推免)", "专业：1(不含推免)")
        );

        assertEquals(0, service.importBatch(directionLevel).admissionPlansCreated());
        assertEquals(0, service.importBatch(conflicting).admissionPlansCreated());
    }

    @Test
    void importsOnlyExplicitRetestContentIdempotently() {
        Catalog408ImportRequest explicit = validRequest(
                "408", "https://yz.chsi.com.cn/zsml/zydetail.do?zydm=081200",
                "计算理论", "复试内容：1.上机考试；2.综合面试。", "f".repeat(64)
        );

        Catalog408ImportResult first = service.importBatch(explicit);
        Catalog408ImportResult second = service.importBatch(explicit);

        assertEquals(1, first.retestRulesCreated());
        assertEquals(0, second.retestRulesCreated());
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM retest_rule rr
                JOIN major m ON m.id = rr.major_id
                JOIN school s ON s.id = rr.school_id
                WHERE s.name = '北京大学' AND m.major_code = '081200' AND rr.year = 2026
                  AND rr.retest_method = '上机/机试 + 面试'
                  AND rr.qualification_line LIKE '复试内容：%'
                  AND rr.remark LIKE '研招网目录复试信息自动结构化｜%'
                """, Integer.class));

        Catalog408ImportRequest referenceOnly = validRequest(
                "408", "https://yz.chsi.com.cn/zsml/zydetail.do?zydm=081200",
                "系统结构", "复试科目见我校研招网公布的招生目录。", "1".repeat(64)
        );
        assertEquals(0, service.importBatch(referenceOnly).retestRulesCreated());
    }

    @Test
    void exposesOfficialNationalBaselineWithoutReplacingSchoolScoreLine() {
        service.importBatch(validRequest("408", "https://yz.chsi.com.cn/zsml/zydetail.do?zydm=081200"));
        Long schoolId = jdbcTemplate.queryForObject("SELECT id FROM school WHERE name = '北京大学'", Long.class);
        var detail = schoolRepository.findDetailById(schoolId);

        assertEquals(1, detail.nationalScoreLines().size());
        assertEquals(264, detail.nationalScoreLines().get(0).totalScore());
        assertEquals("A", detail.nationalScoreLines().get(0).candidateType());
        assertFalse(detail.nationalScoreLines().get(0).applicable());
        assertTrue(detail.scoreLines().isEmpty());
    }

    private Catalog408ImportRequest withQuotaTexts(Catalog408ImportRequest request, List<String> quotaTexts) {
        Catalog408ImportRequest.CatalogRecord current = request.records().get(0);
        Catalog408ImportRequest.CatalogRecord changed = new Catalog408ImportRequest.CatalogRecord(
                current.school(), current.college(), current.major(), current.subjects(), current.source(),
                current.directions(), quotaTexts, current.majorRemarks(), current.catalogRecordIds()
        );
        return new Catalog408ImportRequest(
                request.schemaVersion(), request.collectorVersion(), request.year(), request.retrievedAt(),
                request.stats(), request.sha256(), List.of(changed)
        );
    }

    private Catalog408ImportRequest validRequest(String professionalCode, String sourceUrl) {
        return validRequest(professionalCode, sourceUrl, "计算理论", "目录人数仅供参考", "a".repeat(64));
    }

    private Catalog408ImportRequest validRequest(String professionalCode, String sourceUrl,
                                                  String direction, String majorRemark, String batchHash) {
        ObjectNode evidence = objectMapper.createObjectNode();
        evidence.put("catalogYear", 2026);
        evidence.put("schoolCode", "10001");
        evidence.put("schoolName", "北京大学");
        evidence.put("collegeCode", "101");
        evidence.put("collegeName", "计算机学院");
        evidence.put("majorCode", "081200");
        evidence.put("majorName", "计算机科学与技术");
        evidence.put("studyMode", "全日制");
        ObjectNode evidenceSubjects = evidence.putObject("subjects");
        evidenceSubjects.putObject("politics").put("code", "101");
        evidenceSubjects.putObject("foreignLanguage").put("code", "201");
        evidenceSubjects.putObject("math").put("code", "301");
        evidenceSubjects.putObject("professional").put("code", professionalCode);
        Catalog408ImportRequest.Subject politics = new Catalog408ImportRequest.Subject("101", "思想政治理论", null);
        Catalog408ImportRequest.Subject english = new Catalog408ImportRequest.Subject("201", "英语（一）", null);
        Catalog408ImportRequest.Subject math = new Catalog408ImportRequest.Subject("301", "数学（一）", null);
        Catalog408ImportRequest.Subject professional = new Catalog408ImportRequest.Subject(
                professionalCode, "计算机学科专业基础", null);
        Catalog408ImportRequest.CatalogRecord record = new Catalog408ImportRequest.CatalogRecord(
                new Catalog408ImportRequest.School("10001", "367878", "北京大学", "11", "北京",
                        true, true, true),
                new Catalog408ImportRequest.College("101", "计算机学院"),
                new Catalog408ImportRequest.Major("081200", "计算机科学与技术", "学硕", "全日制"),
                new Catalog408ImportRequest.Subjects(politics, english, math, professional),
                new Catalog408ImportRequest.Source("2026年目录", "研招网招生专业目录", sourceUrl,
                        true, "中国研究生招生信息网", evidence, "b".repeat(64)),
                List.of(new Catalog408ImportRequest.Direction("01", direction)),
                List.of("专业：19(不含推免)"),
                List.of(majorRemark),
                List.of("record-1")
        );
        return new Catalog408ImportRequest(
                1, "test", 2026, "2026-07-13T08:00:00Z",
                new Catalog408ImportRequest.CatalogStats(false, 1, 1, 1, 1, 1),
                batchHash, List.of(record)
        );
    }
}
