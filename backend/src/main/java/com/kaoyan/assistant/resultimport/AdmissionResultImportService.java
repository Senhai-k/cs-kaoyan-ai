package com.kaoyan.assistant.resultimport;

import com.kaoyan.assistant.quality.EvidenceBatchChangedEvent;
import com.kaoyan.assistant.source.DocumentSourceDto;
import com.kaoyan.assistant.source.DocumentSourceRepository;
import com.kaoyan.assistant.source.StructuredEvidenceValidator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class AdmissionResultImportService {

    private static final int MAX_RECORDS = 10_000;
    private static final Pattern ADMISSION_LIST_SOURCE = Pattern.compile(".*(?:拟录取.*名单|录取名单).*", Pattern.DOTALL);
    private final AdmissionResultImportRepository repository;
    private final StructuredEvidenceValidator evidenceValidator;
    private final DocumentSourceRepository sourceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdmissionResultImportService(AdmissionResultImportRepository repository,
                                        StructuredEvidenceValidator evidenceValidator,
                                        DocumentSourceRepository sourceRepository,
                                        ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.evidenceValidator = evidenceValidator;
        this.sourceRepository = sourceRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<AdmissionResultImportBatchDto> list() {
        return repository.findAll();
    }

    public AdmissionResultImportPreview preview(AdmissionResultImportRequest request) {
        validateRequest(request);
        return buildPreview(request.schoolId(), request.records());
    }

    @Transactional
    public AdmissionResultImportDraft createDraft(AdmissionResultImportRequest request) {
        validateRequest(request);
        AdmissionResultImportPreview preview = buildPreview(request.schoolId(), request.records());
        AdmissionResultImportBatchDto existing = repository.findByHash(request.batchSha256());
        if (existing != null) {
            ensureSameBatch(existing, request);
            return new AdmissionResultImportDraft(existing, previewForBatch(existing), true);
        }
        long batchId = repository.createBatch(request, preview);
        request.records().forEach(record -> repository.insertCandidate(batchId, record));
        return new AdmissionResultImportDraft(repository.findById(batchId), preview, false);
    }

    @Transactional
    public AdmissionResultImportPublishResult publish(long batchId) {
        AdmissionResultImportBatchDto batch = requireBatch(batchId);
        evidenceValidator.validate(batch.schoolId(), batch.sourceId());
        AdmissionResultImportPreview preview = previewForBatch(batch);
        if (!preview.publishable()) {
            String failures = preview.groups().stream()
                    .filter(group -> !"MATCHED".equals(group.mappingStatus()))
                    .map(group -> group.groupKey() + ": " + group.mappingMessage())
                    .limit(5).reduce((left, right) -> left + "；" + right).orElse("存在未映射分组");
            throw new IllegalArgumentException("拟录取聚合草稿不可发布：" + failures);
        }

        int existingCount = 0;
        for (AdmissionResultImportPreview.GroupPreview group : preview.groups()) {
            AdmissionResultImportRepository.ExistingResult existing = repository.findExistingResult(
                    group.majorId(), batch.year());
            if (existing == null) continue;
            if (existing.remark() != null && existing.remark().startsWith("拟录取名单批次｜" + batchId + "｜")) {
                existingCount++;
                continue;
            }
            throw new IllegalArgumentException(group.groupKey() + " 已存在其他录取结果，禁止自动覆盖");
        }
        if ("PUBLISHED".equals(batch.status())) {
            return new AdmissionResultImportPublishResult(batchId, batch.status(), 0, existingCount);
        }

        int created = 0;
        for (AdmissionResultImportPreview.GroupPreview group : preview.groups()) {
            if (repository.findExistingResult(group.majorId(), batch.year()) != null) continue;
            repository.createAdmissionResult(batch.schoolId(), group.collegeId(), group.majorId(), batch.year(),
                    group, batch.sourceId(), batchId);
            created++;
        }
        repository.markPublished(batchId);
        eventPublisher.publishEvent(new EvidenceBatchChangedEvent(Set.of(batch.schoolId())));
        return new AdmissionResultImportPublishResult(batchId, "PUBLISHED", created, existingCount);
    }

    private AdmissionResultImportPreview previewForBatch(AdmissionResultImportBatchDto batch) {
        List<AdmissionResultImportRequest.CandidateRecord> records = repository.findCandidates(batch.id()).stream()
                .map(row -> new AdmissionResultImportRequest.CandidateRecord(
                        row.candidateKey(), row.collegeName(), row.majorCode(), row.majorName(), row.degreeType(),
                        row.studyMode(), row.candidateType(), row.initialScore(), row.retestScore(), row.finalScore(),
                        row.specialProgram()
                )).toList();
        return buildPreview(batch.schoolId(), records);
    }

    private AdmissionResultImportPreview buildPreview(long schoolId,
                                                       List<AdmissionResultImportRequest.CandidateRecord> records) {
        Map<String, List<AdmissionResultImportRequest.CandidateRecord>> grouped = new LinkedHashMap<>();
        for (AdmissionResultImportRequest.CandidateRecord record : records) {
            grouped.computeIfAbsent(groupKey(record), ignored -> new ArrayList<>()).add(record);
        }
        List<AdmissionResultImportPreview.GroupPreview> groups = grouped.entrySet().stream()
                .map(entry -> previewGroup(schoolId, entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(AdmissionResultImportPreview.GroupPreview::groupKey))
                .toList();
        int mapped = (int) groups.stream().filter(group -> "MATCHED".equals(group.mappingStatus())).count();
        return new AdmissionResultImportPreview(records.size(), groups.size(), mapped,
                mapped == groups.size(), groups);
    }

    private AdmissionResultImportPreview.GroupPreview previewGroup(
            long schoolId, String groupKey, List<AdmissionResultImportRequest.CandidateRecord> records) {
        AdmissionResultImportRequest.CandidateRecord first = records.get(0);
        List<Integer> scores = records.stream().map(AdmissionResultImportRequest.CandidateRecord::initialScore)
                .filter(value -> value != null).toList();
        boolean completeScores = scores.size() == records.size();
        Integer lowest = completeScores ? scores.stream().min(Integer::compareTo).orElse(null) : null;
        Integer highest = completeScores ? scores.stream().max(Integer::compareTo).orElse(null) : null;
        Double average = completeScores
                ? Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0) * 100.0) / 100.0
                : null;

        String status;
        String message;
        Long collegeId = null;
        Long majorId = null;
        if (!"普通计划".equals(first.candidateType()) || hasText(first.specialProgram())) {
            status = "UNSUPPORTED_SCOPE";
            message = "专项计划或非普通计划需独立口径，当前不自动聚合到专业普通计划";
        } else {
            List<AdmissionResultImportRepository.MajorMatch> matches = repository.findMajorMatches(
                    schoolId, first.collegeName().trim(), first.majorCode().trim(),
                    first.degreeType().trim(), first.studyMode().trim());
            if (matches.isEmpty()) {
                status = "UNMAPPED";
                message = "学校库中未找到同学院、专业代码、学位类型和学习方式";
            } else if (matches.size() > 1) {
                status = "AMBIGUOUS";
                message = "匹配到多个专业，需先清理基础档案";
            } else {
                status = "MATCHED";
                message = completeScores ? "专业唯一匹配，初试分完整" : "专业唯一匹配，仅发布录取人数，分数保持为空";
                collegeId = matches.get(0).collegeId();
                majorId = matches.get(0).majorId();
            }
        }
        return new AdmissionResultImportPreview.GroupPreview(
                groupKey, first.collegeName(), first.majorCode(), first.majorName(), first.degreeType(),
                first.studyMode(), first.candidateType(), normalizeNullable(first.specialProgram()), records.size(),
                scores.size(), lowest, average, highest, collegeId, majorId, status, message
        );
    }

    private void validateRequest(AdmissionResultImportRequest request) {
        if (request == null) throw new IllegalArgumentException("拟录取名单批次不能为空");
        if (request.schemaVersion() == null || request.schemaVersion() != 1) {
            throw new IllegalArgumentException("仅支持 schemaVersion=1 的拟录取名单批次");
        }
        if (request.schoolId() == null) throw new IllegalArgumentException("缺少学校编号");
        if (request.year() == null || request.year() < 2000 || request.year() > 2100) {
            throw new IllegalArgumentException("年份必须在 2000-2100 之间");
        }
        if (!"拟录取名单".equals(request.documentType())) {
            throw new IllegalArgumentException("资料类型必须明确为拟录取名单");
        }
        evidenceValidator.validate(request.schoolId(), request.sourceId());
        DocumentSourceDto source = sourceRepository.findById(request.sourceId());
        String sourceLabel = (source.title() == null ? "" : source.title()) + " "
                + (source.sourceType() == null ? "" : source.sourceType());
        if (!ADMISSION_LIST_SOURCE.matcher(sourceLabel).matches()) {
            throw new IllegalArgumentException("官方证据必须明确标记为拟录取名单，复试方案或复试名单不能代替");
        }
        requireSha256(request.sourceSha256(), "原始名单 SHA-256");
        requireSha256(request.batchSha256(), "结构化批次 SHA-256");
        if (request.records() == null || request.records().isEmpty()) {
            throw new IllegalArgumentException("拟录取名单没有候选人记录");
        }
        if (request.records().size() > MAX_RECORDS) throw new IllegalArgumentException("单批最多导入10000条记录");
        Set<String> keys = new HashSet<>();
        for (AdmissionResultImportRequest.CandidateRecord record : request.records()) {
            validateRecord(record);
            if (!keys.add(record.candidateKey().toLowerCase())) {
                throw new IllegalArgumentException("批次存在重复匿名候选人键: " + record.candidateKey());
            }
        }
    }

    private void ensureSameBatch(AdmissionResultImportBatchDto existing, AdmissionResultImportRequest request) {
        List<String> requestedKeys = request.records().stream()
                .map(record -> record.candidateKey().toLowerCase()).sorted().toList();
        boolean metadataMatches = Objects.equals(existing.schoolId(), request.schoolId())
                && Objects.equals(existing.year(), request.year())
                && Objects.equals(existing.sourceId(), request.sourceId())
                && existing.sourceSha256().equalsIgnoreCase(request.sourceSha256())
                && Objects.equals(existing.inputRecords(), request.records().size());
        if (!metadataMatches || !repository.findCandidateKeys(existing.id()).equals(requestedKeys)) {
            throw new IllegalArgumentException("批次 SHA-256 已存在，但学校、年份、来源或候选人集合不一致");
        }
    }

    private void validateRecord(AdmissionResultImportRequest.CandidateRecord record) {
        if (record == null) throw new IllegalArgumentException("候选人记录不能为空");
        requireSha256(record.candidateKey(), "匿名候选人键");
        requireText(record.collegeName(), "学院名称");
        requireText(record.majorCode(), "专业代码");
        requireText(record.degreeType(), "学位类型");
        requireText(record.studyMode(), "学习方式");
        requireText(record.candidateType(), "考生类型");
        validateIntegerScore(record.initialScore(), 0, 500, "初试总分");
        validateDecimalScore(record.retestScore(), 0, 500, "复试成绩");
        validateDecimalScore(record.finalScore(), 0, 500, "总成绩");
    }

    private String groupKey(AdmissionResultImportRequest.CandidateRecord record) {
        return String.join("|", record.collegeName().trim(), record.majorCode().trim(),
                record.degreeType().trim(), record.studyMode().trim(), record.candidateType().trim(),
                normalizeNullable(record.specialProgram()) == null ? "普通" : record.specialProgram().trim());
    }

    private AdmissionResultImportBatchDto requireBatch(long batchId) {
        AdmissionResultImportBatchDto batch = repository.findById(batchId);
        if (batch == null) throw new IllegalArgumentException("拟录取导入批次不存在");
        return batch;
    }

    private void validateIntegerScore(Integer value, int min, int max, String label) {
        if (value != null && (value < min || value > max)) {
            throw new IllegalArgumentException(label + "必须在 " + min + "-" + max + " 之间");
        }
    }

    private void validateDecimalScore(Double value, double min, double max, String label) {
        if (value != null && (!Double.isFinite(value) || value < min || value > max)) {
            throw new IllegalArgumentException(label + "必须在 " + min + "-" + max + " 之间");
        }
    }

    private void requireText(String value, String label) {
        if (!hasText(value)) throw new IllegalArgumentException("缺少" + label);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeNullable(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private void requireSha256(String value, String label) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(label + "必须是64位十六进制");
        }
    }
}
