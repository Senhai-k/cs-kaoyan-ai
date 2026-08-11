package com.kaoyan.assistant.recommendation;

import com.kaoyan.assistant.school.AdmissionResultInfo;
import com.kaoyan.assistant.school.SchoolDetail;
import com.kaoyan.assistant.school.SchoolService;
import com.kaoyan.assistant.school.SchoolSummary;
import com.kaoyan.assistant.school.SourceInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 20;

    private final SchoolService schoolService;

    public RecommendationService(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    public List<RecommendationItem> recommend(RecommendationRequest request) {
        RecommendationRequest safeRequest = request == null
                ? new RecommendationRequest(null, List.of(), null, null, null, null)
                : request;
        int limit = clampLimit(safeRequest.limit());
        List<SchoolSummary> summaries = schoolService.list(null, null, null, null, null, null, null, null, null, null);
        return summaries.stream()
                .map((summary) -> score(summary, schoolService.fullDetail(summary.id()), safeRequest))
                .sorted(Comparator.comparingInt(RecommendationItem::matchScore).reversed()
                        .thenComparing((item) -> item.benchmarkScore() == null ? Integer.MAX_VALUE : item.benchmarkScore())
                        .thenComparing((item) -> item.school().id()))
                .limit(limit)
                .toList();
    }

    private RecommendationItem score(SchoolSummary summary, SchoolDetail detail, RecommendationRequest request) {
        List<String> reasons = new ArrayList<>();
        int score = 50;
        Integer benchmarkScore = benchmarkScore(summary, detail);
        Integer scoreGap = request.targetScore() == null || benchmarkScore == null ? null : request.targetScore() - benchmarkScore;

        Set<String> preferredProvinces = normalizeProvinces(request.preferredProvinces());
        if (!preferredProvinces.isEmpty()) {
            if (preferredProvinces.contains(summary.province())) {
                score += 18;
                reasons.add("省份偏好匹配：" + summary.province());
            } else {
                score -= 6;
            }
        }

        if (request.degreeType() != null && !request.degreeType().isBlank() && detail != null) {
            if (request.degreeType().trim().equals(detail.degreeType())) {
                score += 12;
                reasons.add("专业类型匹配：" + detail.degreeType());
            } else {
                score -= 8;
            }
        }

        if (request.prefer408() != null) {
            if (summary.is408() == null) {
                score -= 4;
                reasons.add("初试专业课尚未核验");
            } else if (request.prefer408().equals(summary.is408())) {
                score += 10;
                reasons.add(Boolean.TRUE.equals(summary.is408()) ? "符合 408 统考偏好" : "符合自命题偏好");
            } else {
                score -= 10;
            }
        }

        if (scoreGap != null) {
            score += scoreFit(scoreGap, request.riskPreference());
            reasons.add("目标分与参考线差值：" + scoreGap + " 分");
        } else if (benchmarkScore != null) {
            reasons.add("参考线约 " + benchmarkScore + " 分，可作为初筛基准");
        } else {
            score -= 6;
            reasons.add("分数参考数据不足，需要优先核验官方来源");
        }

        if (summary.latestQuota() != null) {
            if (summary.latestQuota() >= 80) {
                score += 8;
                reasons.add("统考名额较充足：" + summary.latestQuota());
            } else if (summary.latestQuota() < 40) {
                score -= 8;
                reasons.add("统考名额偏少：" + summary.latestQuota());
            }
        }

        int officialSourceCount = detail == null ? 0 : (int) detail.sources().stream().filter(SourceInfo::official).count();
        if (officialSourceCount > 0) {
            score += Math.min(10, officialSourceCount * 3);
            reasons.add("已有官方来源 " + officialSourceCount + " 条");
        } else {
            score -= 8;
            reasons.add("官方来源不足，建议进入详情核验");
        }

        if (detail != null && detail.retestRules().stream().anyMatch((rule) -> rule.retestRatio() != null && rule.retestRatio() >= 1.5)) {
            score -= 6;
            reasons.add("复试差额比例偏高，复试淘汰压力需关注");
        }
        if (detail != null && detail.referenceBooks().size() >= 4) {
            score -= 3;
            reasons.add("参考书目较多，专业课复习负担偏重");
        }

        String riskLevel = riskLevel(scoreGap);
        String groupTag = groupTag(scoreGap);
        if (reasons.isEmpty()) {
            reasons.add("按当前结构化数据综合排序");
        }
        return new RecommendationItem(summary, Math.max(0, Math.min(100, score)), groupTag, riskLevel,
                scoreGap, benchmarkScore, officialSourceCount, reasons.stream().limit(4).toList());
    }

    private Integer benchmarkScore(SchoolSummary summary, SchoolDetail detail) {
        Integer latestScoreLine = summary.latestScoreLine();
        Integer latestLowestScore = detail == null ? null : detail.admissionResults().stream()
                .map(AdmissionResultInfo::lowestScore)
                .filter((score) -> score != null)
                .max(Integer::compareTo)
                .orElse(null);
        if (latestScoreLine == null) {
            return latestLowestScore;
        }
        if (latestLowestScore == null) {
            return latestScoreLine;
        }
        return Math.max(latestScoreLine, latestLowestScore);
    }

    private int scoreFit(int scoreGap, String riskPreference) {
        String preference = riskPreference == null ? "BALANCED" : riskPreference.trim().toUpperCase(Locale.ROOT);
        if ("CONSERVATIVE".equals(preference)) {
            if (scoreGap >= 30) return 22;
            if (scoreGap >= 10) return 16;
            if (scoreGap >= 0) return 8;
            return -18;
        }
        if ("AGGRESSIVE".equals(preference)) {
            if (scoreGap >= -10 && scoreGap <= 20) return 22;
            if (scoreGap > 20) return 10;
            if (scoreGap >= -25) return 8;
            return -16;
        }
        if (scoreGap >= 10 && scoreGap <= 35) return 22;
        if (scoreGap >= 0) return 16;
        if (scoreGap >= -15) return 8;
        if (scoreGap > 35) return 10;
        return -18;
    }

    private String riskLevel(Integer scoreGap) {
        if (scoreGap == null) return "UNKNOWN";
        if (scoreGap >= 25) return "LOW";
        if (scoreGap >= 5) return "MEDIUM";
        if (scoreGap >= -15) return "STRETCH";
        return "HIGH";
    }

    private String groupTag(Integer scoreGap) {
        if (scoreGap == null) return "待核验";
        if (scoreGap >= 25) return "保底";
        if (scoreGap >= 5) return "稳妥";
        return "冲刺";
    }

    private Set<String> normalizeProvinces(List<String> provinces) {
        Set<String> result = new HashSet<>();
        if (provinces == null) {
            return result;
        }
        for (String province : provinces) {
            if (province != null && !province.isBlank()) {
                result.add(province.trim());
            }
        }
        return result;
    }

    private int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }
}
