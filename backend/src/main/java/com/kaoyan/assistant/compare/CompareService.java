package com.kaoyan.assistant.compare;

import com.kaoyan.assistant.school.SchoolService;
import com.kaoyan.assistant.school.SourceInfo;
import com.kaoyan.assistant.school.SchoolDetail;
import com.kaoyan.assistant.school.SchoolSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CompareService {

    private final SchoolService schoolService;

    public CompareService(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    public CompareResult compare(List<Long> ids) {
        List<SchoolDetail> details = ids.stream()
                .distinct()
                .limit(4)
                .map(schoolService::fullDetail)
                .filter(detail -> detail != null)
                .toList();
        List<CompareSchoolItem> schools = details.stream()
                .map(this::toCompareItem)
                .toList();
        return new CompareResult(schools, buildRiskTips(details, schools));
    }

    private CompareSchoolItem toCompareItem(SchoolDetail detail) {
        SchoolSummary summary = detail.summary();
        int officialSourceCount = (int) detail.sources().stream()
                .filter(SourceInfo::official)
                .count();
        String latestSourceUpdatedAt = detail.sources().stream()
                .map(SourceInfo::updatedAt)
                .filter(value -> value != null && !value.isBlank())
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new CompareSchoolItem(
                summary.id(),
                summary.name(),
                regionLabel(summary),
                summary.schoolLevel(),
                detail.collegeName(),
                detail.majorName(),
                detail.degreeType(),
                summary.primarySubject(),
                summary.is408(),
                summary.latestQuota(),
                summary.latestScoreLine(),
                detail.quotas(),
                detail.scoreLines(),
                officialSourceCount,
                latestSourceUpdatedAt
        );
    }

    private List<String> buildRiskTips(List<SchoolDetail> details, List<CompareSchoolItem> schools) {
        List<String> tips = new ArrayList<>();
        for (int i = 0; i < schools.size(); i++) {
            CompareSchoolItem school = schools.get(i);
            SchoolDetail detail = details.get(i);
            if (school.latestQuota() != null && school.latestQuota() < 50) {
                tips.add(school.name() + " 最近目录总计划较少，需进一步核验统考与推免口径。");
            }
            if (school.latestScoreLine() != null && school.latestScoreLine() >= 350) {
                tips.add(school.name() + " 复试线较高，需要关注近年录取最低分和复试淘汰情况。");
            }
            if (Boolean.FALSE.equals(school.is408())) {
                tips.add(school.name() + " 使用自命题专业课，备考资料和真题稳定性需要单独核查。");
            }
            if (school.is408() == null) {
                tips.add(school.name() + " 当前尚未核验初试专业课，不能判断是否使用 408。");
            }
            if (school.officialSourceCount() == 0) {
                tips.add(school.name() + " 当前缺少可直接展示的官方资料来源，结论可信度需要额外核验。");
            }
            detail.admissionResults().stream()
                    .filter(result -> result.lowestScore() != null && result.lowestScore() >= 360)
                    .findFirst()
                    .ifPresent(result -> tips.add(school.name() + " 最近录取最低分达到 " + result.lowestScore() + "，分数门槛偏高。"));
            detail.retestRules().stream()
                    .filter(rule -> rule.retestRatio() != null && rule.retestRatio() >= 1.5)
                    .findFirst()
                    .ifPresent(rule -> tips.add(school.name() + " 最近复试差额比例为 " + String.format("%.2f", rule.retestRatio()) + "，复试淘汰压力较大。"));
            if (detail.adjustmentInfos().isEmpty()) {
                tips.add(school.name() + " 当前未收录已核验调剂信息，不能据此判断调剂空间。");
            } else if (detail.adjustmentInfos().stream().noneMatch(adjustment -> adjustment.open())) {
                tips.add(school.name() + " 已收录的调剂信息中没有开放记录，仍需核查当年官方公告。");
            }
            if (detail.referenceBooks().size() >= 4) {
                tips.add(school.name() + " 已收录参考书目较多，专业课复习负担需要提前评估。");
            }
        }
        if (tips.isEmpty() && !schools.isEmpty()) {
            tips.add("当前对比对象未触发明显风险规则，仍需结合官方公告和拟录取名单进一步判断。");
        }
        return tips;
    }

    private String regionLabel(SchoolSummary summary) {
        if (summary.city() == null || summary.city().isBlank()) {
            return summary.province();
        }
        return summary.province() + " " + summary.city();
    }
}
