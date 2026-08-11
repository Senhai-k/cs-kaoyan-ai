package com.kaoyan.assistant;

import com.kaoyan.assistant.school.SchoolRepository;
import com.kaoyan.assistant.school.SchoolSummary;
import com.kaoyan.assistant.ai.AiChatResponse;
import com.kaoyan.assistant.ai.AiChatService;
import com.kaoyan.assistant.source.StructuredEvidenceValidator;
import com.kaoyan.assistant.school.SchoolDetail;
import com.kaoyan.assistant.retest.RetestRuleRequest;
import com.kaoyan.assistant.retest.RetestRuleService;
import com.kaoyan.assistant.score.ScoreLineRequest;
import jakarta.validation.Validator;
import com.kaoyan.assistant.rag.ParsedSourceDocumentDraft;
import com.kaoyan.assistant.rag.SourceDocumentService;
import com.kaoyan.assistant.quality.DataCoverageReport;
import com.kaoyan.assistant.quality.DataCoverageService;
import com.kaoyan.assistant.quality.SchoolCoverageItem;
import com.kaoyan.assistant.quality.DataCollectionTask;
import com.kaoyan.assistant.quality.DataCollectionTaskUpdateRequest;
import com.kaoyan.assistant.quality.DataCollectionTargetRequest;
import com.kaoyan.assistant.quality.OfficialLinkCandidateAcceptRequest;
import com.kaoyan.assistant.recommendation.RecommendationRequest;
import com.kaoyan.assistant.recommendation.RecommendationService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
class VerifiedSeedDataTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private SourceDocumentService sourceDocumentService;

    @Autowired
    private DataCoverageService dataCoverageService;

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private StructuredEvidenceValidator evidenceValidator;

    @Autowired
    private Validator validator;

    @Autowired
    private RetestRuleService retestRuleService;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seedDataDoesNotContainSyntheticBusinessFacts() {
        Integer sampleRemarks = jdbcTemplate.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM admission_result WHERE remark LIKE '%样例%') +
                  (SELECT COUNT(*) FROM retest_rule WHERE remark LIKE '%样例%') +
                  (SELECT COUNT(*) FROM reference_book WHERE remark LIKE '%样例%') +
                  (SELECT COUNT(*) FROM adjustment_info WHERE remark LIKE '%样例%')
                """, Integer.class);
        Integer fakeUrls = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM adjustment_info WHERE notice_url LIKE '%example.edu%'
                """, Integer.class);

        assertThat(sampleRemarks).isZero();
        assertThat(fakeUrls).isZero();
    }

    @Test
    void seedDataContainsVerifiedOfficialSources() {
        Integer officialSources = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM document_source
                WHERE is_official = 1 AND audit_status = 'PUBLISHED'
                """, Integer.class);
        Integer officialDocuments = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM source_document
                WHERE source_reliability = 'OFFICIAL' AND audit_status = 'PUBLISHED'
                """, Integer.class);
        Integer verifiedHustRule = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM retest_rule
                WHERE school_id = 6 AND year = 2026
                  AND retest_ratio = 1.20
                  AND initial_score_weight = 60
                  AND retest_score_weight = 40
                """, Integer.class);

        assertThat(officialSources).isGreaterThanOrEqualTo(17);
        assertThat(officialDocuments).isGreaterThanOrEqualTo(8);
        assertThat(verifiedHustRule).isEqualTo(1);
    }

    @Test
    void unverifiedExamSubjectRemainsUnknown() {
        SchoolSummary school = schoolRepository.findSummaryById(3L);

        assertThat(school).isNotNull();
        assertThat(school.primarySubject()).isNull();
        assertThat(school.is408()).isNull();
        assertThat(school.latestQuota()).isNull();
        assertThat(school.latestScoreLine()).isNull();
    }

    @Test
    void buptAdjustmentNoticeDoesNotInventExamSubject() {
        SchoolSummary school = schoolRepository.findSummaryById(8L);
        Integer officialNoticeDocuments = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM source_document
                WHERE school_id = 8 AND year = 2027
                  AND source_reliability = 'OFFICIAL'
                  AND audit_status = 'PUBLISHED'
                """, Integer.class);

        assertThat(school).isNotNull();
        assertThat(school.primarySubject()).isNull();
        assertThat(school.is408()).isNull();
        assertThat(officialNoticeDocuments).isEqualTo(1);
    }

    @Test
    void schoolSpecificQuestionDoesNotMixOtherSchoolEvidence() {
        AiChatResponse response = aiChatService.chat("北京邮电大学2027年计算机科学与技术是否改考408？");

        assertThat(response.answer()).contains("不能据此判断是否采用 408");
        assertThat(response.sources()).allMatch(source -> source.contains("北京邮电大学"));
        assertThat(response.sources()).noneMatch(source -> source.contains("西安电子科技大学"));
    }

    @Test
    void structuredEvidenceMustBeOfficialPublishedAndBelongToSchool() {
        evidenceValidator.validate(9L, 14L);

        assertThatThrownBy(() -> evidenceValidator.validate(9L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须关联官方证据");
        assertThatThrownBy(() -> evidenceValidator.validate(8L, 14L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("学校不一致");
    }

    @Test
    void detailMetricsExposeTheirExactOfficialEvidence() {
        SchoolDetail xidian = schoolRepository.findDetailById(9L);
        SchoolDetail hust = schoolRepository.findDetailById(6L);

        assertThat(xidian.quotas()).singleElement().extracting("sourceId").isEqualTo(14L);
        assertThat(xidian.examSourceId()).isEqualTo(14L);
        assertThat(xidian.sources()).anyMatch(source -> source.id().equals(14L));
        assertThat(hust.retestRules()).singleElement().extracting("sourceId").isEqualTo(13L);
        assertThat(hust.sources()).anyMatch(source -> source.id().equals(13L));
    }

    @Test
    void comparisonExposesVerifiedYearSeriesForTrendDecisions() throws Exception {
        mockMvc.perform(get("/api/compare").param("ids", "9", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schools[0].quotaHistory[0].year").value(2026))
                .andExpect(jsonPath("$.data.schools[0].quotaHistory[0].value").value(70))
                .andExpect(jsonPath("$.data.schools[1].scoreLineHistory[0].year").value(2026))
                .andExpect(jsonPath("$.data.schools[1].scoreLineHistory[0].value").value(382));
    }

    @Test
    void structuredNumericRangesAndRetestWeightsAreValidated() {
        ScoreLineRequest invalidScore = new ScoreLineRequest(
                9L, 9L, 9L, 1900, 501, null, null, null, null, 14L, null
        );
        RetestRuleRequest invalidWeights = new RetestRuleRequest(
                6L, 6L, 6L, 2026, null, null, 1.2, 60, 30,
                null, null, 13L, null
        );

        assertThat(validator.validate(invalidScore)).hasSizeGreaterThanOrEqualTo(2);
        assertThatThrownBy(() -> retestRuleService.create(invalidWeights))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("合计 100");
    }

    @Test
    @Transactional
    void schoolLevelRetestRulesRemainUnboundAndAppearInSchoolDetails() {
        var created = retestRuleService.create(new RetestRuleRequest(
                6L, null, null, 2025, "3月下旬", "现场复试", null, 60, 40,
                "复试成绩不合格者不予录取。", "以学校和学院通知为准。", 13L,
                "学校级规则，不绑定学院或专业。"
        ));

        assertThat(created.scopeType()).isEqualTo("SCHOOL");
        assertThat(created.collegeId()).isNull();
        assertThat(created.majorId()).isNull();
        assertThat(schoolRepository.findDetailById(6L).retestRules())
                .anyMatch(rule -> rule.scopeType().equals("SCHOOL") && rule.sourceId().equals(13L));

        assertThatThrownBy(() -> retestRuleService.create(new RetestRuleRequest(
                6L, 5L, 6L, 2025, null, "现场复试", null, null, null,
                null, null, 13L, null
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("归属不一致");
    }

    @Test
    void sourceDocumentListsOmitLargeBodiesButDetailsKeepThem() {
        var summaries = sourceDocumentService.list(null, "ALL");
        assertThat(summaries).isNotEmpty().allSatisfy(document -> assertThat(document.rawText()).isNull());

        var detail = sourceDocumentService.detail(summaries.get(0).id());
        assertThat(detail.rawText()).isNotBlank();
    }

    @Test
    void xidianCatalogFactsAreStructuredWithSource() {
        SchoolSummary school = schoolRepository.findSummaryById(9L);
        Integer sourcedPlan = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admission_plan
                WHERE school_id = 9 AND major_id = 9 AND year = 2026
                  AND total_quota = 70 AND unified_quota = 70 AND source_id = 14
                """, Integer.class);

        assertThat(school).isNotNull();
        assertThat(school.primarySubject()).isEqualTo("408 计算机学科专业基础");
        assertThat(school.is408()).isTrue();
        assertThat(school.latestQuota()).isEqualTo(70);
        assertThat(sourcedPlan).isEqualTo(1);
    }

    @Test
    void sjtuOfficialCatalogProvides408WithoutInventingMajorQuota() {
        SchoolSummary school = schoolRepository.findSummaryById(5L);
        Integer exactSources = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM document_source
                WHERE school_id = 5 AND source_url = 'https://yzb.sjtu.edu.cn/post/3309'
                  AND is_official = 1 AND audit_status = 'PUBLISHED'
                """, Integer.class);
        Integer majorPlans = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admission_plan WHERE school_id = 5 AND major_id = 5
                """, Integer.class);

        assertThat(school.primarySubject()).isEqualTo("408 计算机学科专业基础");
        assertThat(school.is408()).isTrue();
        assertThat(school.latestQuota()).isNull();
        assertThat(exactSources).isEqualTo(1);
        assertThat(majorPlans).isZero();
    }

    @Test
    void njuOfficialCatalogProvidesSourced408AndMajorQuotaWithoutInventingBreakdown() {
        SchoolSummary school = schoolRepository.findSummaryById(2L);
        Integer exactPlans = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admission_plan p
                JOIN document_source s ON s.id = p.source_id
                WHERE p.school_id = 2 AND p.major_id = 2 AND p.year = 2026
                  AND p.total_quota = 66
                  AND p.recommended_quota IS NULL
                  AND p.unified_quota IS NULL
                  AND s.source_url = 'https://yzb.nju.edu.cn/19/b1/c47862a793009/page.htm'
                """, Integer.class);

        assertThat(school.primarySubject()).isEqualTo("408 计算机学科专业基础");
        assertThat(school.is408()).isTrue();
        assertThat(school.latestQuota()).isEqualTo(66);
        assertThat(exactPlans).isEqualTo(1);
    }

    @Test
    void zjuRetestPlanKeepsUnifiedQuotaSeparateFromAnnualCatalogQuota() {
        SchoolDetail detail = schoolRepository.findDetailById(4L);
        Integer verifiedMetrics = jdbcTemplate.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM admission_plan
                   WHERE school_id = 4 AND major_id = 4 AND year = 2026
                     AND total_quota IS NULL AND recommended_quota IS NULL AND unified_quota = 9) +
                  (SELECT COUNT(*) FROM score_line
                   WHERE school_id = 4 AND major_id = 4 AND year = 2026
                     AND total_score = 382 AND politics_score = 50
                     AND foreign_language_score = 50 AND math_score = 75 AND professional_score = 75) +
                  (SELECT COUNT(*) FROM retest_rule
                   WHERE school_id = 4 AND major_id = 4 AND year = 2026
                     AND retest_ratio = 1.30 AND initial_score_weight = 65 AND retest_score_weight = 35)
                """, Integer.class);

        assertThat(detail.summary().primarySubject()).isNull();
        assertThat(detail.summary().latestQuota()).isNull();
        assertThat(detail.summary().latestScoreLine()).isEqualTo(382);
        assertThat(detail.admissionPlans()).singleElement().satisfies(plan -> {
            assertThat(plan.totalQuota()).isNull();
            assertThat(plan.unifiedQuota()).isEqualTo(9);
        });
        assertThat(verifiedMetrics).isEqualTo(3);
    }

    @Test
    void schoolsCanBeFilteredByExactProvince() {
        List<SchoolSummary> schools = schoolRepository.findSummaries(
                null, null, "江苏", null, null, null, null, null, null, null
        );

        assertThat(schools).extracting(SchoolSummary::name).contains("南京大学");
        assertThat(schools).allMatch(school -> "江苏".equals(school.province()));
    }

    @Test
    void recommendationsRewardExactProvincePreference() {
        var recommendations = recommendationService.recommend(
                new RecommendationRequest(null, List.of("江苏"), null, null, null, 20)
        );
        var nju = recommendations.stream()
                .filter(item -> "南京大学".equals(item.school().name()))
                .findFirst()
                .orElseThrow();

        assertThat(nju.reasons()).contains("省份偏好匹配：江苏");
    }

    @Test
    void coverageReportExposesRealDataGapsWithoutInventingValues() {
        DataCoverageReport report = dataCoverageService.report();
        SchoolCoverageItem xidian = report.schools().stream()
                .filter(item -> item.schoolId().equals(9L))
                .findFirst()
                .orElseThrow();
        SchoolCoverageItem hust = report.schools().stream()
                .filter(item -> item.schoolId().equals(6L))
                .findFirst()
                .orElseThrow();
        SchoolCoverageItem zju = report.schools().stream()
                .filter(item -> item.schoolId().equals(4L))
                .findFirst()
                .orElseThrow();

        assertThat(report.schoolCount()).isEqualTo(10);
        assertThat(report.officialSourceCount()).isGreaterThanOrEqualTo(14);
        assertThat(report.officialDocumentCount()).isGreaterThanOrEqualTo(6);
        assertThat(report.averageCoveragePercent()).isBetween(50, 99);
        assertThat(report.readySchoolCount()).isZero();
        assertThat(report.dimensions()).hasSize(10);
        assertThat(report.dimensions()).anyMatch(item -> item.key().equals("admissionPlan")
                && item.label().equals("招生计划") && item.totalSchoolCount() == 10);
        assertThat(report.dimensions()).anyMatch(item -> item.key().equals("nationalBaseline")
                && item.label().equals("国家线基准") && item.coveredSchoolCount() == 10);
        assertThat(report.dimensions()).anyMatch(item -> item.key().equals("schoolBaseline")
                && item.label().equals("学校基本线") && item.coveredSchoolCount() == 0
                && item.totalSchoolCount() == 6);
        assertThat(xidian.examSubjectCount()).isEqualTo(1);
        assertThat(xidian.admissionPlanCount()).isEqualTo(1);
        assertThat(xidian.missingDimensions()).doesNotContain("考试科目", "招生计划");
        assertThat(hust.retestRuleCount()).isEqualTo(1);
        assertThat(hust.missingDimensions()).doesNotContain("复试规则");
        assertThat(zju.coveragePercent()).isEqualTo(70);
        assertThat(zju.missingDimensions()).containsExactly("考试科目", "学校基本线", "录取结果");
    }

    @Test
    void collectionTasksTurnCoverageGapsIntoPrioritizedWork() {
        List<DataCollectionTask> tasks = dataCoverageService.collectionTasks(5);

        assertThat(tasks).hasSize(5).isSortedAccordingTo(
                java.util.Comparator.comparingInt(DataCollectionTask::priorityScore).reversed()
                        .thenComparing(DataCollectionTask::schoolName)
        );
        assertThat(tasks).allSatisfy(task -> {
            assertThat(task.priority()).isIn("P0", "P1", "P2");
            assertThat(task.targetYears()).hasSize(3);
            assertThat(task.missingDimensions()).isNotEmpty();
            assertThat(task.recommendedDocumentTypes()).isNotEmpty();
            assertThat(task.reason()).contains("覆盖率");
            assertThat(task.status()).isEqualTo("OPEN");
            assertThat(task.dueDate()).isNotBlank();
            assertThat(task.completionCriteria()).contains("官方资料", "结构化录入");
        });
        DataCollectionTask sjtu = dataCoverageService.collectionTasks(100, "ALL").stream()
                .filter(task -> task.schoolName().equals("上海交通大学"))
                .findFirst().orElseThrow();
        assertThat(sjtu.officialEntryUrl()).isEqualTo("https://yzb.sjtu.edu.cn");
        assertThat(sjtu.targets()).isNotEmpty().allSatisfy(target -> {
            assertThat(target.sourceUrl()).isEqualTo(sjtu.officialEntryUrl());
            assertThat(target.status()).isEqualTo("PENDING");
            assertThat(target.systemGenerated()).isTrue();
        });
        assertThat(sjtu.history()).anyMatch(item -> item.action().equals("TASK_CREATED"));
    }

    @Test
    @Transactional
    void collectionTaskAssignmentsAndStatusArePersisted() {
        DataCollectionTask task = dataCoverageService.collectionTasks(20).stream()
                .filter(item -> item.schoolName().equals("上海交通大学"))
                .findFirst()
                .orElseThrow();

        DataCollectionTask updated = dataCoverageService.updateTask(task.schoolId(),
                new DataCollectionTaskUpdateRequest(
                        "IN_PROGRESS", "数据运营-A", "2026-08-01", "完成近三年官方目录采集并关联结构化字段"
                ), "test-admin");
        DataCollectionTask reloaded = dataCoverageService.collectionTasks(100, "ALL").stream()
                .filter(item -> item.schoolId().equals(task.schoolId()))
                .findFirst()
                .orElseThrow();

        assertThat(updated.status()).isEqualTo("IN_PROGRESS");
        assertThat(reloaded.assignee()).isEqualTo("数据运营-A");
        assertThat(reloaded.dueDate()).isEqualTo("2026-08-01");
        assertThat(reloaded.completionCriteria()).isEqualTo("完成近三年官方目录采集并关联结构化字段");
        assertThat(reloaded.history()).anyMatch(item -> item.action().equals("MANUAL_UPDATE")
                && "test-admin".equals(item.operator()));
    }

    @Test
    @Transactional
    void incompleteTaskCannotBeMarkedCompleted() {
        DataCollectionTask task = dataCoverageService.collectionTasks(1).get(0);

        assertThatThrownBy(() -> dataCoverageService.updateTask(task.schoolId(),
                new DataCollectionTaskUpdateRequest(
                        "COMPLETED", null, task.dueDate(), task.completionCriteria()
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能标记为完成");
    }

    @Test
    void collectionTaskUpdatesRequireAdministratorAuthentication() throws Exception {
        mockMvc.perform(put("/api/data-coverage/tasks/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/data-coverage/tasks/5/targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/data-coverage/tasks/5/targets/13/discover-links"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void collectionTargetCrudIsAudited() {
        var created = dataCoverageService.createTarget(5L, new DataCollectionTargetRequest(
                "上海交通大学2026复试线待采集", "复试分数线", 2026,
                "https://yzb.sjtu.edu.cn/example", "PENDING", "等待替换为精确公告"
        ), "test-admin");
        var updated = dataCoverageService.updateTarget(5L, created.id(), new DataCollectionTargetRequest(
                created.title(), created.documentType(), created.targetYear(), created.sourceUrl(),
                "COLLECTED", "已获取正文，等待核验"
        ), "test-admin");

        assertThat(created.systemGenerated()).isFalse();
        assertThat(updated.status()).isEqualTo("COLLECTED");
        assertThat(dataCoverageService.collectionTasks(100, "ALL").stream()
                .filter(task -> task.schoolId().equals(5L)).findFirst().orElseThrow().history())
                .anyMatch(item -> item.action().equals("TARGET_UPDATED")
                        && "test-admin".equals(item.operator()));

        dataCoverageService.deleteTarget(5L, created.id(), "test-admin");
        assertThat(dataCoverageService.collectionTasks(100, "ALL").stream()
                .filter(task -> task.schoolId().equals(5L)).findFirst().orElseThrow().targets())
                .noneMatch(target -> target.id().equals(created.id()));
    }

    @Test
    @Transactional
    void collectionTargetRejectsUntrustedUrlSchemes() {
        assertThatThrownBy(() -> dataCoverageService.createTarget(5L, new DataCollectionTargetRequest(
                "非法地址", "复试分数线", 2026, "javascript:alert(1)", "PENDING", null
        ), "test-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP(S)");
    }

    @Test
    @Transactional
    void discoveredOfficialLinkRequiresExplicitAcceptanceAndIsAudited() {
        var created = dataCoverageService.createTarget(5L, new DataCollectionTargetRequest(
                "上海交通大学2026复试线待采集", "复试分数线", 2026,
                "https://yzb.sjtu.edu.cn/", "COLLECTED", null
        ), "test-admin");

        var accepted = dataCoverageService.acceptOfficialLink(
                5L, created.id(),
                new OfficialLinkCandidateAcceptRequest("https://yzb.sjtu.edu.cn/post/3310"),
                "reviewer-a"
        );

        assertThat(accepted.sourceUrl()).isEqualTo("https://yzb.sjtu.edu.cn/post/3310");
        assertThat(accepted.status()).isEqualTo("PENDING");
        assertThat(accepted.note()).contains("reviewer-a", "人工确认");
        assertThat(dataCoverageService.collectionTasks(100, "ALL").stream()
                .filter(task -> task.schoolId().equals(5L)).findFirst().orElseThrow().history())
                .anyMatch(item -> item.action().equals("TARGET_LINK_ACCEPTED")
                        && "reviewer-a".equals(item.operator()));
    }

    @Test
    @Transactional
    void publishedEvidenceEventAutomaticallyCompletesSatisfiedTask() {
        dataCoverageService.collectionTasks(100, "ALL");
        jdbcTemplate.update("""
                INSERT INTO score_line (school_id, college_id, major_id, year, total_score, source_id)
                VALUES (9, 9, 9, 2026, 350, 14)
                """);
        jdbcTemplate.update("""
                INSERT INTO admission_result (school_id, college_id, major_id, year, admitted_count, source_id)
                VALUES (9, 9, 9, 2026, 60, 14)
                """);
        jdbcTemplate.update("""
                INSERT INTO retest_rule (school_id, college_id, major_id, year, retest_method, source_id)
                VALUES (9, 9, 9, 2026, '线下复试', 14)
                """);

        sourceDocumentService.create(new com.kaoyan.assistant.rag.SourceDocumentRequest(
                "西安电子科技大学任务闭环测试资料", "招生专业目录", "https://gr.xidian.edu.cn/test",
                9L, 9L, 9L, 2026, "PUBLISHED", "OFFICIAL",
                "用于验证官方资料发布事件会重新计算覆盖率并自动关闭已满足完成条件的任务。", "测试事务回滚"
        ));

        DataCollectionTask completed = dataCoverageService.collectionTasks(100, "ALL").stream()
                .filter(item -> item.schoolId().equals(9L))
                .findFirst()
                .orElseThrow();
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.missingDimensions()).isEmpty();
        assertThat(completed.completedAt()).isNotBlank();
        assertThat(completed.history()).anyMatch(item -> item.action().equals("AUTO_COMPLETED"));
    }

    @Test
    void parsesTextBasedPdfIntoDraft() throws IOException {
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("2026 official admission catalog 408");
                content.endText();
            }
            document.save(output);
            pdfBytes = output.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "official-catalog.pdf", "application/pdf", pdfBytes
        );
        ParsedSourceDocumentDraft draft = sourceDocumentService.parseTextFile(file, "招生专业目录");

        assertThat(draft.title()).isEqualTo("official-catalog");
        assertThat(draft.rawText()).contains("official admission catalog 408");
        assertThat(draft.remark()).contains("PDF");
    }
}
