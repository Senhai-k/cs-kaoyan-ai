package com.kaoyan.assistant.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaoyan.assistant.quality.EvidenceBatchChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class Catalog408ImportService {

    private static final int MAX_RECORDS = 10_000;
    private static final Pattern PROFESSIONAL_UNIFIED_QUOTA = Pattern.compile(
            "^专业[：:]\\s*(\\d+)\\s*[（(]不含推免[）)]\\s*$"
    );
    private static final List<Pattern> EXPLICIT_RETEST_PATTERNS = List.of(
            Pattern.compile("复试(?:内容|科目(?:考核)?|笔试科目|方式)\\s*[：:]"),
            Pattern.compile("复试包含"),
            Pattern.compile("复试中的面试\\s*[：:]"),
            Pattern.compile("(?:^|[\\n；。])一、复试\\s*[：:]")
    );
    private final Catalog408ImportRepository repository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public Catalog408ImportService(Catalog408ImportRepository repository, ObjectMapper objectMapper,
                                   ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Catalog408ImportResult importBatch(Catalog408ImportRequest request) {
        validateBatch(request);
        request.records().forEach(record -> validateRecord(request.year(), record));
        int schoolsCreated = 0;
        int collegesCreated = 0;
        int majorsCreated = 0;
        int sourcesCreated = 0;
        int documentsCreated = 0;
        int subjectsCreated = 0;
        int admissionPlansCreated = 0;
        int retestRulesCreated = 0;
        Set<Long> changedSchoolIds = new HashSet<>();
        Map<String, SafeAdmissionPlan> safePlans = safeAdmissionPlans(request.records());
        Map<String, SafeRetestRule> safeRetestRules = safeRetestRules(request.records());
        Set<String> processedPlanKeys = new HashSet<>();
        Set<String> processedRetestKeys = new HashSet<>();

        for (Catalog408ImportRequest.CatalogRecord record : request.records()) {
            String region = regionFor(record.school().province());
            String level = schoolLevel(record.school());
            Catalog408ImportRepository.UpsertResult school = repository.upsertSchool(record.school(), region, level);
            changedSchoolIds.add(school.id());
            Catalog408ImportRepository.UpsertResult college = repository.upsertCollege(school.id(), record.college());
            String directions = joinDirections(record.directions());
            String majorRemark = joinNonBlank(record.majorRemarks());
            Catalog408ImportRepository.UpsertResult major = repository.upsertMajor(
                    school.id(), college.id(), record.major(), directions, majorRemark);
            String sourceTitle = request.year() + "年研招网目录 - " + record.school().name() + " "
                    + record.college().name() + " " + record.major().code() + " " + record.major().studyMode()
                    + " " + subjectCodeKey(record.subjects());
            Catalog408ImportRepository.UpsertResult source = repository.upsertSource(
                    school.id(), college.id(), request.year(), record.source(), sourceTitle);
            String documentTitle = sourceTitle + " 408科目证据";
            String evidenceText = evidenceText(request.year(), record);
            Catalog408ImportRepository.UpsertResult document = repository.upsertDocument(
                    school.id(), college.id(), major.id(), request.year(), documentTitle,
                    record.source().url(), evidenceText, record.source().sha256());
            Catalog408ImportRepository.UpsertResult subject = repository.upsertExamSubject(
                    school.id(), college.id(), major.id(), request.year(),
                    display(record.subjects().politics()), display(record.subjects().foreignLanguage()),
                    display(record.subjects().math()), display(record.subjects().professional()), source.id());
            String planKey = majorKey(record);
            SafeAdmissionPlan safePlan = safePlans.get(planKey);
            if (safePlan != null && processedPlanKeys.add(planKey)) {
                Catalog408ImportRepository.UpsertResult plan = repository.upsertCatalogAdmissionPlan(
                        school.id(), college.id(), major.id(), request.year(), safePlan.unifiedQuota(),
                        source.id(), safePlan.quotaText()
                );
                admissionPlansCreated += plan.created() ? 1 : 0;
            }
            SafeRetestRule safeRetestRule = safeRetestRules.get(planKey);
            if (safeRetestRule != null && processedRetestKeys.add(planKey)) {
                Catalog408ImportRepository.UpsertResult retestRule = repository.upsertCatalogRetestRule(
                        school.id(), college.id(), major.id(), request.year(), safeRetestRule.method(),
                        safeRetestRule.ruleText(), source.id()
                );
                retestRulesCreated += retestRule.created() ? 1 : 0;
            }
            schoolsCreated += school.created() ? 1 : 0;
            collegesCreated += college.created() ? 1 : 0;
            majorsCreated += major.created() ? 1 : 0;
            sourcesCreated += source.created() ? 1 : 0;
            documentsCreated += document.created() ? 1 : 0;
            subjectsCreated += subject.created() ? 1 : 0;
        }
        int existing = request.records().size() - subjectsCreated;
        repository.saveBatch(request);
        eventPublisher.publishEvent(new EvidenceBatchChangedEvent(changedSchoolIds));
        return new Catalog408ImportResult(request.year(), request.stats().complete(), request.records().size(),
                schoolsCreated, collegesCreated, majorsCreated, sourcesCreated, documentsCreated,
                subjectsCreated, admissionPlansCreated, retestRulesCreated, existing);
    }

    public Catalog408ImportStatus latestStatus() {
        return repository.findLatestBatch();
    }

    private void validateBatch(Catalog408ImportRequest request) {
        if (request == null) throw new IllegalArgumentException("导入批次不能为空");
        if (request.schemaVersion() == null || request.schemaVersion() != 1) {
            throw new IllegalArgumentException("仅支持 schemaVersion=1 的408目录批次");
        }
        if (request.year() == null || request.year() < 2000 || request.year() > 2100) {
            throw new IllegalArgumentException("目录年份必须在 2000-2100 之间");
        }
        if (request.stats() == null) throw new IllegalArgumentException("缺少采集统计信息");
        if (request.records() == null || request.records().isEmpty()) {
            throw new IllegalArgumentException("408目录批次没有可导入记录");
        }
        if (request.records().size() > MAX_RECORDS) throw new IllegalArgumentException("单批最多导入10000条记录");
        if (request.stats().records() != null && request.stats().records() != request.records().size()) {
            throw new IllegalArgumentException("采集统计记录数与实际记录数不一致");
        }
        requireSha256(request.sha256(), "批次 SHA-256");
        Set<String> keys = new HashSet<>();
        for (Catalog408ImportRequest.CatalogRecord record : request.records()) {
            String key = recordKey(record);
            if (!keys.add(key)) throw new IllegalArgumentException("批次存在重复408记录: " + key);
        }
    }

    private void validateRecord(int year, Catalog408ImportRequest.CatalogRecord record) {
        if (record == null || record.school() == null || record.college() == null || record.major() == null
                || record.subjects() == null || record.source() == null) {
            throw new IllegalArgumentException("408目录记录缺少学校、院系、专业、科目或来源");
        }
        requireText(record.school().code(), "招生单位代码");
        requireText(record.school().name(), "招生单位名称");
        requireText(record.college().code(), "院系代码");
        requireText(record.college().name(), "院系名称");
        requireText(record.major().code(), "专业代码");
        requireText(record.major().name(), "专业名称");
        validateSubject(record.subjects().politics(), "政治");
        validateSubject(record.subjects().foreignLanguage(), "外国语");
        validateSubject(record.subjects().math(), "业务课一");
        validateSubject(record.subjects().professional(), "业务课二");
        if (!"408".equals(record.subjects().professional().code())) {
            throw new IllegalArgumentException(record.school().name() + " " + record.major().code() + " 的第四科不是408");
        }
        if (!record.source().official()) throw new IllegalArgumentException("408记录来源必须标记为官方");
        validateOfficialUrl(record.source().url());
        requireSha256(record.source().sha256(), "来源 SHA-256");
        if (record.source().rawEvidence() == null || !record.source().rawEvidence().isObject()) {
            throw new IllegalArgumentException("来源必须包含研招网原始证据对象");
        }
        assertEvidenceValue(record.source(), "catalogYear", String.valueOf(year));
        assertEvidenceValue(record.source(), "schoolCode", record.school().code());
        assertEvidenceValue(record.source(), "schoolName", record.school().name());
        assertEvidenceValue(record.source(), "collegeCode", record.college().code());
        assertEvidenceValue(record.source(), "collegeName", record.college().name());
        assertEvidenceValue(record.source(), "majorCode", record.major().code());
        assertEvidenceValue(record.source(), "majorName", record.major().name());
        assertEvidenceValue(record.source(), "studyMode", record.major().studyMode());
        assertEvidenceSubject(record.source(), "politics", record.subjects().politics().code());
        assertEvidenceSubject(record.source(), "foreignLanguage", record.subjects().foreignLanguage().code());
        assertEvidenceSubject(record.source(), "math", record.subjects().math().code());
        assertEvidenceSubject(record.source(), "professional", record.subjects().professional().code());
    }

    private void validateOfficialUrl(String value) {
        requireText(value, "官方来源 URL");
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"yz.chsi.com.cn".equalsIgnoreCase(uri.getHost())
                    || uri.getPath() == null || !uri.getPath().startsWith("/zsml/")) {
                throw new IllegalArgumentException("408目录来源必须是研招网 HTTPS 专业目录页面");
            }
        } catch (IllegalArgumentException error) {
            if (error.getMessage().contains("必须是研招网")) throw error;
            throw new IllegalArgumentException("官方来源 URL 格式无效");
        }
    }

    private void assertEvidenceValue(Catalog408ImportRequest.Source source, String field, String expected) {
        String actual = source.rawEvidence().path(field).asText();
        if (!expected.equals(actual)) throw new IllegalArgumentException("来源原始证据字段不一致: " + field);
    }

    private void assertEvidenceSubject(Catalog408ImportRequest.Source source, String field, String expectedCode) {
        String actual = source.rawEvidence().path("subjects").path(field).path("code").asText();
        if (!expectedCode.equals(actual)) throw new IllegalArgumentException("来源原始证据科目不一致: " + field);
    }

    private String evidenceText(int year, Catalog408ImportRequest.CatalogRecord record) {
        String directions = joinDirections(record.directions());
        String quotas = joinNonBlank(record.quotaTexts());
        StringBuilder text = new StringBuilder()
                .append("中国研究生招生信息网 ").append(year).append(" 年硕士专业目录公开记录。")
                .append("招生单位：").append(record.school().code()).append(' ').append(record.school().name()).append("；")
                .append("院系：").append(record.college().code()).append(' ').append(record.college().name()).append("；")
                .append("专业：").append(record.major().code()).append(' ').append(record.major().name()).append("；")
                .append("学习方式：").append(record.major().studyMode()).append("；")
                .append("初试科目：").append(display(record.subjects().politics())).append("、")
                .append(display(record.subjects().foreignLanguage())).append("、")
                .append(display(record.subjects().math())).append("、")
                .append(display(record.subjects().professional())).append("。")
                .append("研究方向：").append(directions.isBlank() ? "未区分" : directions).append("。");
        if (!quotas.isBlank()) text.append("目录人数原文：").append(quotas).append("；不转换为结构化招生计划。") ;
        try {
            text.append("\n原始证据 JSON：").append(objectMapper.writeValueAsString(record.source().rawEvidence()));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("原始证据无法序列化", error);
        }
        return text.toString();
    }

    private String recordKey(Catalog408ImportRequest.CatalogRecord record) {
        if (record == null || record.school() == null || record.college() == null || record.major() == null
                || record.subjects() == null || record.subjects().professional() == null) return "INVALID";
        return String.join("|", record.school().code(), record.college().code(), record.major().code(),
                String.valueOf(record.major().studyMode()), subjectCodeKey(record.subjects()));
    }

    private Map<String, SafeAdmissionPlan> safeAdmissionPlans(List<Catalog408ImportRequest.CatalogRecord> records) {
        Map<String, LinkedHashSet<String>> quotaTextsByMajor = new LinkedHashMap<>();
        for (Catalog408ImportRequest.CatalogRecord record : records) {
            LinkedHashSet<String> values = quotaTextsByMajor.computeIfAbsent(
                    majorKey(record), ignored -> new LinkedHashSet<>()
            );
            if (record.quotaTexts() != null) {
                record.quotaTexts().stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .forEach(values::add);
            }
        }
        Map<String, SafeAdmissionPlan> safe = new LinkedHashMap<>();
        quotaTextsByMajor.forEach((key, values) -> {
            Set<Integer> quotas = new LinkedHashSet<>();
            boolean allProfessional = !values.isEmpty();
            for (String value : values) {
                Matcher matcher = PROFESSIONAL_UNIFIED_QUOTA.matcher(value);
                if (!matcher.matches()) {
                    allProfessional = false;
                    break;
                }
                try {
                    quotas.add(Integer.parseInt(matcher.group(1)));
                } catch (NumberFormatException exception) {
                    allProfessional = false;
                    break;
                }
            }
            if (allProfessional && quotas.size() == 1) {
                safe.put(key, new SafeAdmissionPlan(quotas.iterator().next(), values.iterator().next()));
            }
        });
        return safe;
    }

    private Map<String, SafeRetestRule> safeRetestRules(List<Catalog408ImportRequest.CatalogRecord> records) {
        Map<String, LinkedHashSet<String>> rulesByMajor = new LinkedHashMap<>();
        for (Catalog408ImportRequest.CatalogRecord record : records) {
            LinkedHashSet<String> values = rulesByMajor.computeIfAbsent(
                    majorKey(record), ignored -> new LinkedHashSet<>()
            );
            if (record.majorRemarks() == null) continue;
            record.majorRemarks().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(this::normalizeRetestText)
                    .filter(this::hasExplicitRetestContent)
                    .forEach(values::add);
        }
        Map<String, SafeRetestRule> safe = new LinkedHashMap<>();
        rulesByMajor.forEach((key, values) -> {
            if (values.isEmpty()) return;
            String ruleText = String.join("\n---\n", values);
            safe.put(key, new SafeRetestRule(summarizeRetestMethod(ruleText), ruleText));
        });
        return safe;
    }

    private boolean hasExplicitRetestContent(String text) {
        return EXPLICIT_RETEST_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }

    private String normalizeRetestText(String text) {
        return text.trim()
                .replaceAll("[\\t ]+", " ")
                .replaceAll(" *\\n *", "\n");
    }

    private String summarizeRetestMethod(String text) {
        boolean machineTest = text.contains("上机") || text.contains("机试") || text.contains("机考");
        boolean interview = text.contains("面试");
        boolean writtenTest = text.contains("笔试");
        if (machineTest && interview) return "上机/机试 + 面试";
        if (writtenTest && interview) return "笔试 + 面试";
        if (machineTest) return "上机/机试";
        if (interview) return "面试";
        if (writtenTest) return "笔试";
        return "专业科目考核";
    }

    private String majorKey(Catalog408ImportRequest.CatalogRecord record) {
        return String.join("|", record.school().code(), record.college().code(), record.major().code(),
                String.valueOf(record.major().degreeType()), String.valueOf(record.major().studyMode()));
    }

    private String subjectCodeKey(Catalog408ImportRequest.Subjects subjects) {
        return String.join("-", subjects.politics().code(), subjects.foreignLanguage().code(),
                subjects.math().code(), subjects.professional().code());
    }

    private void validateSubject(Catalog408ImportRequest.Subject subject, String label) {
        if (subject == null) throw new IllegalArgumentException(label + "科目不能为空");
        requireText(subject.code(), label + "科目代码");
        requireText(subject.name(), label + "科目名称");
    }

    private String display(Catalog408ImportRequest.Subject subject) {
        return subject.code().trim() + " " + subject.name().trim();
    }

    private String joinDirections(List<Catalog408ImportRequest.Direction> directions) {
        if (directions == null) return "";
        return directions.stream().filter(item -> item != null && item.name() != null && !item.name().isBlank())
                .map(Catalog408ImportRequest.Direction::name).distinct().collect(Collectors.joining("、"));
    }

    private String joinNonBlank(List<String> values) {
        if (values == null) return "";
        return values.stream().filter(value -> value != null && !value.isBlank()).distinct()
                .collect(Collectors.joining("；"));
    }

    private void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
    }

    private void requireSha256(String value, String label) {
        if (value == null || !value.toLowerCase(Locale.ROOT).matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + "格式无效");
        }
    }

    private String schoolLevel(Catalog408ImportRequest.School school) {
        if (school.is985()) return "985/211/双一流";
        if (school.is211() && school.isDoubleFirstClass()) return "211/双一流";
        if (school.is211()) return "211";
        if (school.isDoubleFirstClass()) return "双一流";
        return "普通院校";
    }

    private String regionFor(String province) {
        if (province == null) return null;
        return switch (province) {
            case "北京", "天津", "河北", "山西", "内蒙古" -> "华北";
            case "辽宁", "吉林", "黑龙江" -> "东北";
            case "上海", "江苏", "浙江", "安徽", "福建", "江西", "山东" -> "华东";
            case "河南", "湖北", "湖南" -> "华中";
            case "广东", "广西", "海南" -> "华南";
            case "重庆", "四川", "贵州", "云南", "西藏" -> "西南";
            case "陕西", "甘肃", "青海", "宁夏", "新疆" -> "西北";
            default -> null;
        };
    }

    private record SafeAdmissionPlan(int unifiedQuota, String quotaText) {
    }

    private record SafeRetestRule(String method, String ruleText) {
    }
}
