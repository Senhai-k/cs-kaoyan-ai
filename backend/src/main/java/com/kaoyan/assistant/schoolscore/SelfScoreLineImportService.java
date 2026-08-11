package com.kaoyan.assistant.schoolscore;

import com.kaoyan.assistant.quality.EvidenceBatchChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SelfScoreLineImportService {

    private static final String AVAILABLE = "AVAILABLE";
    private static final String NOT_PUBLISHED = "NOT_PUBLISHED";
    private static final int EXPECTED_SELF_SCORE_SCHOOLS = 34;
    private final SelfScoreLineImportRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public SelfScoreLineImportService(SelfScoreLineImportRepository repository,
                                      ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SelfScoreLineImportResult importBatch(SelfScoreLineImportRequest request) {
        validateBatch(request);
        request.records().forEach(this::validateLine);
        ResolvedSchools resolvedSchools = resolveSchoolIds(request.records());
        Map<String, Long> schoolIds = resolvedSchools.ids();
        int scoreLinesCreated = 0;
        int sourcesCreated = 0;
        int documentsCreated = 0;
        Set<Long> changedSchoolIds = new HashSet<>();

        for (SelfScoreLineImportRequest.ReviewedLine line : request.records()) {
            Long schoolId = schoolIds.get(line.schoolName());
            changedSchoolIds.add(schoolId);
            SelfScoreLineImportRepository.UpsertResult source = repository.upsertSource(
                    schoolId, request.year(), line);
            SelfScoreLineImportRepository.UpsertResult scoreLine = repository.upsertScoreLine(
                    schoolId, source.id(), request.year(), line);
            SelfScoreLineImportRepository.UpsertResult document = repository.upsertDocument(
                    schoolId, request.year(), line, evidenceText(request, line));
            sourcesCreated += source.created() ? 1 : 0;
            scoreLinesCreated += scoreLine.created() ? 1 : 0;
            documentsCreated += document.created() ? 1 : 0;
        }
        repository.saveBatch(request);
        eventPublisher.publishEvent(new EvidenceBatchChangedEvent(changedSchoolIds));
        return new SelfScoreLineImportResult(
                request.year(), request.records().size(), request.stats().schools(),
                request.stats().available(), request.stats().unavailable(), resolvedSchools.created(), scoreLinesCreated,
                sourcesCreated, documentsCreated, request.records().size() - scoreLinesCreated
        );
    }

    public SelfScoreLineImportStatus latestStatus() {
        return repository.findLatestBatch();
    }

    private ResolvedSchools resolveSchoolIds(List<SelfScoreLineImportRequest.ReviewedLine> records) {
        Map<String, Long> schoolIds = new LinkedHashMap<>();
        List<SelfScoreLineImportRequest.ReviewedLine> missing = new java.util.ArrayList<>();
        for (SelfScoreLineImportRequest.ReviewedLine line : records) {
            Long schoolId = repository.findSchoolId(line.schoolName());
            if (schoolId == null) {
                requireSchoolMetadata(line);
                missing.add(line);
            } else {
                schoolIds.put(line.schoolName(), schoolId);
            }
        }
        for (SelfScoreLineImportRequest.ReviewedLine line : missing) {
            schoolIds.put(line.schoolName(), repository.createSchool(line));
        }
        return new ResolvedSchools(schoolIds, missing.size());
    }

    private void requireSchoolMetadata(SelfScoreLineImportRequest.ReviewedLine line) {
        requireText(line.province(), line.schoolName() + "省份");
        requireText(line.city(), line.schoolName() + "城市");
        requireText(line.schoolLevel(), line.schoolName() + "院校层次");
        if (line.is985() == null || line.is211() == null || line.isDoubleFirstClass() == null) {
            throw new IllegalArgumentException("学校库缺少" + line.schoolName() + "，且核验批次未提供完整院校属性");
        }
    }

    private void validateBatch(SelfScoreLineImportRequest request) {
        if (request == null) throw new IllegalArgumentException("导入批次不能为空");
        if (request.schemaVersion() == null || request.schemaVersion() != 1) {
            throw new IllegalArgumentException("仅支持 schemaVersion=1 的自主划线批次");
        }
        if (request.year() == null || request.year() < 2000 || request.year() > 2100) {
            throw new IllegalArgumentException("年份必须在 2000-2100 之间");
        }
        requireText(request.publisher(), "发布平台");
        validateUrl(request.portalUrl(), List.of("yz.chsi.com.cn"), "汇总入口");
        requireSha256(request.sourceBatchSha256(), "原始采集批次 SHA-256");
        requireSha256(request.batchSha256(), "核验批次 SHA-256");
        if (request.stats() == null || request.records() == null || request.records().isEmpty()) {
            throw new IllegalArgumentException("缺少采集统计或核验记录");
        }
        int available = (int) request.records().stream()
                .filter(line -> AVAILABLE.equals(line.availabilityStatus())).count();
        int unavailable = request.records().size() - available;
        long schools = request.records().stream().map(SelfScoreLineImportRequest.ReviewedLine::schoolName)
                .distinct().count();
        if (request.stats().schools() == null || request.stats().schools() != schools
                || request.stats().available() == null || request.stats().available() != available
                || request.stats().unavailable() == null || request.stats().unavailable() != unavailable) {
            throw new IllegalArgumentException("核验统计与实际记录不一致");
        }
        if (schools != EXPECTED_SELF_SCORE_SCHOOLS) {
            throw new IllegalArgumentException("自主划线院校批次必须覆盖34所学校，当前为" + schools + "所");
        }
        Set<String> keys = new HashSet<>();
        for (SelfScoreLineImportRequest.ReviewedLine line : request.records()) {
            String key = line.schoolName() + "|" + line.categoryCode() + "|" + line.degreeType();
            if (!keys.add(key)) throw new IllegalArgumentException("核验批次存在重复记录: " + key);
        }
    }

    private void validateLine(SelfScoreLineImportRequest.ReviewedLine line) {
        if (line == null) throw new IllegalArgumentException("核验记录不能为空");
        requireText(line.schoolName(), "学校名称");
        requireText(line.title(), "来源标题");
        requireText(line.categoryCode(), "学科代码");
        requireText(line.categoryName(), "学科名称");
        requireText(line.degreeType(), "学位类型");
        requireText(line.scopeNote(), "适用范围说明");
        validateUrl(line.articleUrl(), List.of("yz.chsi.com.cn"), "官方文章");
        requireSha256(line.articleSha256(), "文章 SHA-256");
        if (line.imageUrl() != null && !line.imageUrl().isBlank()) {
            validateUrl(line.imageUrl(), List.of("chei.com.cn"), "官方表格图片");
            requireSha256(line.imageSha256(), "表格图片 SHA-256");
        }
        if (!AVAILABLE.equals(line.availabilityStatus()) && !NOT_PUBLISHED.equals(line.availabilityStatus())) {
            throw new IllegalArgumentException(line.schoolName() + " 的可用状态无效");
        }
        if (NOT_PUBLISHED.equals(line.availabilityStatus())) {
            if (line.totalScore() != null || hasSpecificScores(line) || hasGenericScores(line)) {
                throw new IllegalArgumentException(line.schoolName() + " 未公布记录不能包含推断分数");
            }
            return;
        }
        validateScore(line.totalScore(), 200, 500, line.schoolName() + " 总分");
        boolean specific = hasSpecificScores(line);
        boolean generic = hasGenericScores(line);
        if (specific == generic) {
            throw new IllegalArgumentException(line.schoolName() + " 必须且只能填写四科明细线或通用单科线");
        }
        if (specific) {
            validateScore(line.politicsScore(), 1, 100, "政治线");
            validateScore(line.foreignLanguageScore(), 1, 100, "外语线");
            validateScore(line.subjectOneScore(), 1, 150, "业务课一线");
            validateScore(line.subjectTwoScore(), 1, 150, "业务课二线");
        } else {
            validateScore(line.score100(), 1, 100, "满分100分科目线");
            validateScore(line.scoreOver100(), 1, 150, "满分大于100分科目线");
        }
    }

    private String evidenceText(SelfScoreLineImportRequest request, SelfScoreLineImportRequest.ReviewedLine line) {
        String scoreText;
        if (NOT_PUBLISHED.equals(line.availabilityStatus())) {
            scoreText = "官方汇总表中计算机相关学院或专业对应分数仍为空，未生成或推断学校基本线。";
        } else if (hasSpecificScores(line)) {
            scoreText = "总分 " + line.totalScore() + "，政治/管理类联考 " + line.politicsScore()
                    + "，外国语 " + line.foreignLanguageScore() + "，业务课一 " + line.subjectOneScore()
                    + "，业务课二 " + line.subjectTwoScore() + "。";
        } else {
            scoreText = "总分 " + line.totalScore() + "，满分100分科目 " + line.score100()
                    + "，满分大于100分科目 " + line.scoreOver100() + "。";
        }
        return line.schoolName() + " " + request.year() + " 年 " + line.categoryName() + "["
                + line.categoryCode() + "]" + line.degreeType() + "学校复试基本要求：" + scoreText
                + "适用范围：" + line.scopeNote() + " 该数据是学校最低基本线，不是学院或具体专业实际复试线。"
                + (line.remark() == null || line.remark().isBlank() ? "" : " 核验备注：" + line.remark());
    }

    private boolean hasSpecificScores(SelfScoreLineImportRequest.ReviewedLine line) {
        return line.politicsScore() != null || line.foreignLanguageScore() != null
                || line.subjectOneScore() != null || line.subjectTwoScore() != null;
    }

    private boolean hasGenericScores(SelfScoreLineImportRequest.ReviewedLine line) {
        return line.score100() != null || line.scoreOver100() != null;
    }

    private void validateScore(Integer value, int min, int max, String label) {
        if (value == null || value < min || value > max) {
            throw new IllegalArgumentException(label + "必须在 " + min + "-" + max + " 之间");
        }
    }

    private void validateUrl(String value, List<String> allowedHosts, String label) {
        requireText(value, label + " URL");
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            boolean allowed = "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && allowedHosts.stream().anyMatch(item -> host.equals(item) || host.endsWith("." + item));
            if (!allowed) throw new IllegalArgumentException(label + "必须使用允许的官方 HTTPS 域名");
        } catch (IllegalArgumentException error) {
            if (error.getMessage() != null && error.getMessage().contains("必须使用")) throw error;
            throw new IllegalArgumentException(label + " URL 格式无效");
        }
    }

    private void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少" + label);
    }

    private void requireSha256(String value, String label) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(label + "必须是64位十六进制");
        }
    }

    private record ResolvedSchools(Map<String, Long> ids, int created) {
    }
}
