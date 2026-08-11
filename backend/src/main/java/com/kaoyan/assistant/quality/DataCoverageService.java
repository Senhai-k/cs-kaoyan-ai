package com.kaoyan.assistant.quality;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.net.URI;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.Year;

@Service
public class DataCoverageService {

    private static final int DIMENSION_COUNT = 10;
    private static final int READY_THRESHOLD = 75;

    private final DataCoverageRepository dataCoverageRepository;
    private final DataCollectionTaskRepository taskRepository;
    private final DataCollectionTargetRepository targetRepository;
    private final DataCollectionTaskHistoryRepository historyRepository;
    private final OfficialLinkDiscoveryService officialLinkDiscoveryService;

    public DataCoverageService(DataCoverageRepository dataCoverageRepository,
                               DataCollectionTaskRepository taskRepository,
                               DataCollectionTargetRepository targetRepository,
                               DataCollectionTaskHistoryRepository historyRepository,
                               OfficialLinkDiscoveryService officialLinkDiscoveryService) {
        this.dataCoverageRepository = dataCoverageRepository;
        this.taskRepository = taskRepository;
        this.targetRepository = targetRepository;
        this.historyRepository = historyRepository;
        this.officialLinkDiscoveryService = officialLinkDiscoveryService;
    }

    public DataCoverageReport report() {
        List<SchoolCoverageItem> schools = dataCoverageRepository.findSchoolCoverageCounts().stream()
                .map(this::toItem)
                .sorted(Comparator.comparingInt(SchoolCoverageItem::coveragePercent)
                        .thenComparing(SchoolCoverageItem::name))
                .toList();
        int schoolCount = schools.size();
        int averageCoverage = schoolCount == 0 ? 0 : (int) Math.round(
                schools.stream().mapToInt(SchoolCoverageItem::coveragePercent).average().orElse(0)
        );
        List<DataCoverageDimension> dimensions = List.of(
                dimension("college", "学院", schools, item -> item.collegeCount() > 0),
                dimension("major", "专业", schools, item -> item.majorCount() > 0),
                dimension("examSubject", "考试科目", schools, item -> item.examSubjectCount() > 0),
                dimension("admissionPlan", "招生计划", schools, item -> item.admissionPlanCount() > 0),
                dimension("nationalBaseline", "国家线基准", schools,
                        item -> item.nationalBaselineCount() > 0 || item.selfDeterminedScore()),
                selfScoreDimension(schools),
                dimension("scoreLine", "院校复试线", schools, item -> item.scoreLineCount() > 0),
                dimension("admissionResult", "录取结果", schools, item -> item.admissionResultCount() > 0),
                dimension("retestRule", "复试规则", schools, item -> item.retestRuleCount() > 0),
                dimension("officialEvidence", "官方证据", schools,
                        item -> item.officialSourceCount() + item.officialDocumentCount() > 0)
        );
        return new DataCoverageReport(
                schoolCount,
                averageCoverage,
                (int) schools.stream().filter(item -> item.coveragePercent() >= READY_THRESHOLD).count(),
                schools.stream().mapToInt(SchoolCoverageItem::officialSourceCount).sum(),
                schools.stream().mapToInt(SchoolCoverageItem::officialDocumentCount).sum(),
                dimensions,
                schools
        );
    }

    public List<DataCollectionTask> collectionTasks(int requestedLimit) {
        return collectionTasks(requestedLimit, "ACTIVE");
    }

    public List<DataCollectionTask> collectionTasks(int requestedLimit, String requestedStatus) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        int currentYear = Year.now().getValue();
        DataCoverageReport report = report();
        Map<Long, SchoolCoverageItem> coverageBySchool = report.schools().stream()
                .collect(Collectors.toMap(SchoolCoverageItem::schoolId, Function.identity()));
        Map<Long, List<DataCollectionTarget>> targetsBySchool = targetRepository.findAll().stream()
                .collect(Collectors.groupingBy(DataCollectionTarget::schoolId));
        Map<Long, List<DataCollectionTaskHistory>> historyBySchool = historyRepository.findAll().stream()
                .collect(Collectors.groupingBy(DataCollectionTaskHistory::schoolId));
        String statusFilter = normalizeStatusFilter(requestedStatus);
        return taskRepository.findAll().stream()
                .filter(task -> matchesStatus(task.status(), statusFilter))
                .map(task -> toCollectionTask(
                        coverageBySchool.get(task.schoolId()), task, currentYear,
                        targetsBySchool.getOrDefault(task.schoolId(), List.of()),
                        historyBySchool.getOrDefault(task.schoolId(), List.of()).stream().limit(8).toList()
                ))
                .filter(task -> task != null)
                .sorted(Comparator.comparing((DataCollectionTask task) -> "COMPLETED".equals(task.status()))
                        .thenComparing(Comparator.comparingInt(DataCollectionTask::priorityScore).reversed())
                        .thenComparing(DataCollectionTask::schoolName))
                .limit(limit)
                .toList();
    }

    @Transactional
    public DataCollectionTask updateTask(Long schoolId, DataCollectionTaskUpdateRequest request) {
        return updateTask(schoolId, request, "system");
    }

    @Transactional
    public DataCollectionTask updateTask(Long schoolId, DataCollectionTaskUpdateRequest request, String operator) {
        SchoolCoverageItem coverage = report().schools().stream()
                .filter(item -> item.schoolId().equals(schoolId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("school not found"));
        synchronizeTask(coverage);
        DataCollectionTaskRepository.TaskState current = taskRepository.findBySchoolId(schoolId);
        if (current == null) {
            throw new IllegalArgumentException("collection task not found");
        }
        String status = normalizeTaskStatus(request == null ? null : request.status(), current.status());
        if ("COMPLETED".equals(status) && !coverage.missingDimensions().isEmpty()) {
            throw new IllegalArgumentException("任务仍有未完成数据维度，不能标记为完成");
        }
        String assignee = normalizeNullable(request == null ? null : request.assignee());
        LocalDate dueDate = parseDueDate(request == null ? null : request.dueDate());
        String criteria = request == null || request.completionCriteria() == null
                ? current.completionCriteria() : request.completionCriteria().trim();
        if (criteria.length() < 10) {
            throw new IllegalArgumentException("完成条件至少需要 10 个字符");
        }
        boolean changed = !status.equals(current.status())
                || !Objects.equals(assignee, current.assignee())
                || !Objects.equals(dueDate == null ? null : dueDate.toString(), current.dueDate())
                || !criteria.equals(current.completionCriteria());
        if (changed) {
            taskRepository.update(
                    schoolId, status, assignee, dueDate, criteria, !criteria.equals(defaultCompletionCriteria(coverage))
            );
            historyRepository.save(
                    schoolId, "MANUAL_UPDATE", current.status(), status, normalizeOperator(operator),
                    manualUpdateDetail(current, assignee, dueDate, criteria)
            );
        }
        return currentTask(coverage, taskRepository.findBySchoolId(schoolId));
    }

    @Transactional
    public DataCollectionTarget createTarget(Long schoolId, DataCollectionTargetRequest request, String operator) {
        requireCoverage(schoolId);
        DataCollectionTargetRequest normalized = normalizeTargetRequest(request);
        Long id = targetRepository.create(schoolId, normalized, false);
        historyRepository.save(
                schoolId, "TARGET_CREATED", null, null, normalizeOperator(operator),
                normalized.targetYear() + "年" + normalized.documentType() + "：" + normalized.sourceUrl()
        );
        return targetRepository.findById(id);
    }

    @Transactional
    public DataCollectionTarget updateTarget(Long schoolId, Long targetId,
                                             DataCollectionTargetRequest request, String operator) {
        DataCollectionTarget current = requireTarget(schoolId, targetId);
        DataCollectionTargetRequest normalized = normalizeTargetRequest(request);
        targetRepository.update(targetId, normalized);
        historyRepository.save(
                schoolId, "TARGET_UPDATED", null, null, normalizeOperator(operator),
                current.status() + " -> " + normalized.status() + "；" + normalized.title()
        );
        return targetRepository.findById(targetId);
    }

    @Transactional
    public void deleteTarget(Long schoolId, Long targetId, String operator) {
        DataCollectionTarget current = requireTarget(schoolId, targetId);
        targetRepository.delete(targetId);
        historyRepository.save(
                schoolId, "TARGET_DELETED", null, null, normalizeOperator(operator), current.title()
        );
    }

    public List<OfficialLinkCandidate> discoverOfficialLinks(Long schoolId, Long targetId) {
        SchoolCoverageItem coverage = requireCoverage(schoolId);
        DataCollectionTarget target = requireTarget(schoolId, targetId);
        if (coverage.officialEntryUrl() == null || coverage.officialEntryUrl().isBlank()) {
            throw new IllegalArgumentException("该院校尚未登记官方研招入口");
        }
        return officialLinkDiscoveryService.discover(coverage.officialEntryUrl(), target);
    }

    @Transactional
    public DataCollectionTarget acceptOfficialLink(Long schoolId, Long targetId,
                                                   OfficialLinkCandidateAcceptRequest request, String operator) {
        SchoolCoverageItem coverage = requireCoverage(schoolId);
        DataCollectionTarget target = requireTarget(schoolId, targetId);
        if (request == null || request.sourceUrl() == null || request.sourceUrl().isBlank()) {
            throw new IllegalArgumentException("候选 URL 不能为空");
        }
        String sourceUrl = officialLinkDiscoveryService.validateAcceptedCandidate(
                coverage.officialEntryUrl(), request.sourceUrl()
        ).toString();
        String reviewer = normalizeOperator(operator);
        String note = target.note() == null || target.note().isBlank() ? "" : target.note().trim() + "；";
        note += "官方链接候选已由 " + reviewer + " 人工确认";
        targetRepository.update(targetId, new DataCollectionTargetRequest(
                target.title(), target.documentType(), target.targetYear(), sourceUrl, "PENDING", note
        ));
        historyRepository.save(
                schoolId, "TARGET_LINK_ACCEPTED", target.status(), "PENDING", reviewer,
                target.title() + "：" + sourceUrl
        );
        return targetRepository.findById(targetId);
    }

    @Transactional
    public DataCollectionTarget verifyAgentTarget(Long targetId, Long schoolId, String sourceUrl,
                                                  String documentType, Integer year, String feedback) {
        DataCollectionTarget target = targetRepository.findById(targetId);
        if (target == null) {
            throw new IllegalArgumentException("collection target not found");
        }
        if (!target.schoolId().equals(schoolId)) {
            throw new IllegalArgumentException("collection target does not belong to document school");
        }
        if (sourceUrl == null || !sourceUrl.trim().equals(target.sourceUrl())) {
            throw new IllegalArgumentException("document source URL does not match collection target");
        }
        if (documentType == null || !documentType.trim().equals(target.documentType())) {
            throw new IllegalArgumentException("document type does not match collection target");
        }
        if (!Objects.equals(year, target.targetYear())) {
            throw new IllegalArgumentException("document year does not match collection target");
        }
        String note = "CoverageWorkflow 已核验并发布";
        if (feedback != null && !feedback.isBlank()) {
            note += "；审核意见：" + feedback.trim();
        }
        if (!"VERIFIED".equals(target.status())) {
            targetRepository.markVerified(targetId, note);
            historyRepository.save(
                    target.schoolId(), "AGENT_TARGET_VERIFIED", target.status(), "VERIFIED",
                    "coverage-workflow", target.title() + "；" + sourceUrl
            );
        }
        return targetRepository.findById(targetId);
    }

    @Transactional
    public void refreshTask(Long schoolId) {
        if (schoolId == null) {
            return;
        }
        report().schools().stream()
                .filter(item -> item.schoolId().equals(schoolId))
                .findFirst()
                .ifPresent(this::synchronizeTask);
    }

    @Transactional
    public void refreshTasks(Set<Long> schoolIds) {
        if (schoolIds == null || schoolIds.isEmpty()) {
            return;
        }
        report().schools().stream()
                .filter(item -> schoolIds.contains(item.schoolId()))
                .forEach(this::synchronizeTask);
    }

    @Transactional
    public void synchronizeAllTasks() {
        report().schools().forEach(this::synchronizeTask);
    }

    private void synchronizeTask(SchoolCoverageItem item) {
        if (item.missingDimensions().isEmpty()) {
            DataCollectionTaskRepository.TaskState current = taskRepository.findBySchoolId(item.schoolId());
            if (taskRepository.markCompleted(item.schoolId())) {
                historyRepository.save(
                        item.schoolId(), "AUTO_COMPLETED", current == null ? null : current.status(),
                        "COMPLETED", "system", "覆盖条件全部满足，任务自动完成"
                );
            }
            synchronizeTargets(item, List.of());
            return;
        }
        TaskPlan plan = taskPlan(item);
        DataCollectionTaskRepository.TaskState previous = taskRepository.findBySchoolId(item.schoolId());
        DataCollectionTaskRepository.SyncChange change = taskRepository.ensureOpenTask(
                item.schoolId(), LocalDate.now().plusDays(dueDays(plan.priority())), defaultCompletionCriteria(item)
        );
        if (change == DataCollectionTaskRepository.SyncChange.CREATED) {
            historyRepository.save(item.schoolId(), "TASK_CREATED", null, "OPEN", "system", plan.reason());
        } else if (change == DataCollectionTaskRepository.SyncChange.REOPENED) {
            historyRepository.save(
                    item.schoolId(), "AUTO_REOPENED", previous == null ? "COMPLETED" : previous.status(),
                    "OPEN", "system", "检测到新的覆盖缺口：" + String.join("、", item.missingDimensions())
            );
        }
        synchronizeTargets(item, plan.documentTypes());
    }

    private DataCollectionTask toCollectionTask(SchoolCoverageItem item,
                                                DataCollectionTaskRepository.TaskState state,
                                                int currentYear,
                                                List<DataCollectionTarget> targets,
                                                List<DataCollectionTaskHistory> history) {
        if (item == null || state == null) {
            return null;
        }
        TaskPlan plan = taskPlan(item);
        boolean completed = "COMPLETED".equals(state.status());
        boolean overdue = !completed && state.dueDate() != null && LocalDate.parse(state.dueDate()).isBefore(LocalDate.now());
        String reason = completed ? "覆盖条件已满足，任务已自动完成" : plan.reason();
        return new DataCollectionTask(
                item.schoolId(), item.name(), item.schoolLevel(), plan.priority(), plan.priorityScore(),
                item.coveragePercent(), List.of(currentYear, currentYear - 1, currentYear - 2),
                item.missingDimensions(), plan.documentTypes(), reason,
                state.status(), state.assignee(), state.dueDate(), state.completionCriteria(), overdue,
                item.officialEntryUrl(), targets, history,
                state.createdAt(), state.updatedAt(), state.completedAt()
        );
    }

    private DataCollectionTask currentTask(SchoolCoverageItem coverage,
                                           DataCollectionTaskRepository.TaskState state) {
        List<DataCollectionTarget> targets = targetRepository.findAll().stream()
                .filter(target -> target.schoolId().equals(coverage.schoolId())).toList();
        List<DataCollectionTaskHistory> history = historyRepository.findAll().stream()
                .filter(item -> item.schoolId().equals(coverage.schoolId())).limit(8).toList();
        return toCollectionTask(coverage, state, Year.now().getValue(), targets, history);
    }

    private void synchronizeTargets(SchoolCoverageItem item, List<String> documentTypes) {
        int changes = targetRepository.synchronizeSystemTargets(
                item.schoolId(), Year.now().getValue(), item.officialEntryUrl(), documentTypes
        );
        if (changes > 0) {
            historyRepository.save(
                    item.schoolId(), "TARGETS_SYNCED", null, null, "system",
                    "根据当前覆盖缺口同步 " + changes + " 条官方 URL 待办"
            );
        }
    }

    private TaskPlan taskPlan(SchoolCoverageItem item) {
        int levelWeight = item.schoolLevel() != null && item.schoolLevel().contains("985") ? 20
                : item.schoolLevel() != null && item.schoolLevel().contains("211") ? 10 : 0;
        int staleWeight = item.latestVerifiedAt() == null || item.latestVerifiedAt().isBlank() ? 10 : 0;
        int priorityScore = item.missingDimensions().size() * 10 + levelWeight + staleWeight;
        String priority = priorityScore >= 60 ? "P0" : priorityScore >= 40 ? "P1" : "P2";
        LinkedHashSet<String> documentTypes = new LinkedHashSet<>();
        for (String dimension : item.missingDimensions()) {
            documentTypes.add(documentTypeFor(dimension));
        }
        String reason = "%s，当前覆盖率 %d%%，缺失 %s".formatted(
                levelWeight > 0 ? "高关注院校优先" : staleWeight > 0 ? "尚无核验时间" : "关键决策字段不足",
                item.coveragePercent(),
                String.join("、", item.missingDimensions())
        );
        return new TaskPlan(priority, priorityScore, List.copyOf(documentTypes), reason);
    }

    private String defaultCompletionCriteria(SchoolCoverageItem item) {
        return "发布可审计的官方资料并完成结构化录入：" + String.join("、", item.missingDimensions());
    }

    private int dueDays(String priority) {
        return switch (priority) {
            case "P0" -> 7;
            case "P1" -> 14;
            default -> 21;
        };
    }

    private String normalizeStatusFilter(String status) {
        String normalized = status == null ? "ACTIVE" : status.trim().toUpperCase();
        Set<String> allowed = Set.of("ACTIVE", "ALL", "OPEN", "IN_PROGRESS", "BLOCKED", "COMPLETED");
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("unsupported task status filter");
        }
        return normalized;
    }

    private boolean matchesStatus(String status, String filter) {
        if ("ALL".equals(filter)) return true;
        if ("ACTIVE".equals(filter)) return !"COMPLETED".equals(status);
        return filter.equals(status);
    }

    private String normalizeTaskStatus(String requested, String fallback) {
        String status = requested == null || requested.isBlank() ? fallback : requested.trim().toUpperCase();
        if (!Set.of("OPEN", "IN_PROGRESS", "BLOCKED", "COMPLETED").contains(status)) {
            throw new IllegalArgumentException("unsupported task status");
        }
        return status;
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SchoolCoverageItem requireCoverage(Long schoolId) {
        return report().schools().stream().filter(item -> item.schoolId().equals(schoolId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("school not found"));
    }

    private DataCollectionTarget requireTarget(Long schoolId, Long targetId) {
        DataCollectionTarget target = targetRepository.findById(targetId);
        if (target == null || !target.schoolId().equals(schoolId)) {
            throw new IllegalArgumentException("collection target not found");
        }
        return target;
    }

    private DataCollectionTargetRequest normalizeTargetRequest(DataCollectionTargetRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("collection target is required");
        }
        if (request.title() == null || request.title().isBlank()
                || request.documentType() == null || request.documentType().isBlank()) {
            throw new IllegalArgumentException("资料标题和类型不能为空");
        }
        int year = request.targetYear() == null ? Year.now().getValue() : request.targetYear();
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("目标年份必须在 2000-2100 之间");
        }
        String status = request.status() == null || request.status().isBlank()
                ? "PENDING" : request.status().trim().toUpperCase();
        if (!Set.of("PENDING", "COLLECTED", "VERIFIED").contains(status)) {
            throw new IllegalArgumentException("unsupported collection target status");
        }
        String sourceUrl = request.sourceUrl() == null ? "" : request.sourceUrl().trim();
        try {
            URI uri = URI.create(sourceUrl);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("资料 URL 必须是可访问的 HTTP(S) 地址");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("资料 URL 必须是可访问的 HTTP(S) 地址");
        }
        return new DataCollectionTargetRequest(
                request.title().trim(), request.documentType().trim(), year, sourceUrl, status,
                normalizeNullable(request.note())
        );
    }

    private String manualUpdateDetail(DataCollectionTaskRepository.TaskState current, String assignee,
                                      LocalDate dueDate, String criteria) {
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(current.assignee(), assignee)) changes.add("负责人已更新");
        if (!Objects.equals(current.dueDate(), dueDate == null ? null : dueDate.toString())) changes.add("截止日期已更新");
        if (!Objects.equals(current.completionCriteria(), criteria)) changes.add("完成条件已更新");
        return changes.isEmpty() ? "任务状态已更新" : String.join("；", changes);
    }

    private String normalizeOperator(String operator) {
        return operator == null || operator.isBlank() ? "system" : operator.trim();
    }

    private LocalDate parseDueDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("截止日期格式必须为 YYYY-MM-DD");
        }
    }

    private String documentTypeFor(String dimension) {
        return switch (dimension) {
            case "学院", "专业", "考试科目", "招生计划" -> "招生专业目录";
            case "学校基本线", "院校复试线" -> "复试分数线";
            case "录取结果" -> "拟录取名单";
            case "复试规则" -> "复试录取细则";
            default -> "官方招生公告";
        };
    }

    private SchoolCoverageItem toItem(SchoolCoverageCounts counts) {
        List<String> missing = new ArrayList<>();
        int covered = 0;
        covered += mark(counts.collegeCount() > 0, "学院", missing);
        covered += mark(counts.majorCount() > 0, "专业", missing);
        covered += mark(counts.examSubjectCount() > 0, "考试科目", missing);
        covered += mark(counts.admissionPlanCount() > 0, "招生计划", missing);
        covered += mark(counts.nationalBaselineCount() > 0 || counts.selfDeterminedScore(), "国家线基准", missing);
        covered += mark(!counts.selfDeterminedScore() || counts.schoolBaselineCount() > 0, "学校基本线", missing);
        covered += mark(counts.scoreLineCount() > 0, "院校复试线", missing);
        covered += mark(counts.admissionResultCount() > 0, "录取结果", missing);
        covered += mark(counts.retestRuleCount() > 0, "复试规则", missing);
        covered += mark(counts.officialSourceCount() + counts.officialDocumentCount() > 0, "官方证据", missing);

        return new SchoolCoverageItem(
                counts.schoolId(),
                counts.name(),
                counts.province(),
                counts.city(),
                counts.schoolLevel(),
                counts.selfDeterminedScore(),
                counts.officialEntryUrl(),
                counts.collegeCount(),
                counts.majorCount(),
                counts.examSubjectCount(),
                counts.admissionPlanCount(),
                counts.nationalBaselineCount(),
                counts.schoolBaselineCount(),
                counts.scoreLineCount(),
                counts.admissionResultCount(),
                counts.retestRuleCount(),
                counts.referenceBookCount(),
                counts.adjustmentInfoCount(),
                counts.officialSourceCount(),
                counts.officialDocumentCount(),
                (int) Math.round(covered * 100.0 / DIMENSION_COUNT),
                List.copyOf(missing),
                latest(counts.latestSourceUpdatedAt(), counts.latestDocumentUpdatedAt())
        );
    }

    private int mark(boolean present, String label, List<String> missing) {
        if (present) {
            return 1;
        }
        missing.add(label);
        return 0;
    }

    private DataCoverageDimension dimension(String key, String label, List<SchoolCoverageItem> schools,
                                            java.util.function.Predicate<SchoolCoverageItem> predicate) {
        int covered = (int) schools.stream().filter(predicate).count();
        int percent = schools.isEmpty() ? 0 : (int) Math.round(covered * 100.0 / schools.size());
        return new DataCoverageDimension(key, label, covered, schools.size(), percent);
    }

    private DataCoverageDimension selfScoreDimension(List<SchoolCoverageItem> schools) {
        List<SchoolCoverageItem> eligible = schools.stream().filter(SchoolCoverageItem::selfDeterminedScore).toList();
        int covered = (int) eligible.stream().filter(item -> item.schoolBaselineCount() > 0).count();
        int percent = eligible.isEmpty() ? 0 : (int) Math.round(covered * 100.0 / eligible.size());
        return new DataCoverageDimension("schoolBaseline", "学校基本线", covered, eligible.size(), percent);
    }

    private String latest(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first.compareTo(second) >= 0 ? first : second;
    }

    private record TaskPlan(String priority, int priorityScore, List<String> documentTypes, String reason) {
    }
}
