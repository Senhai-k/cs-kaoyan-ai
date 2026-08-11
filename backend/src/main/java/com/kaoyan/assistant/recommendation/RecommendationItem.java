package com.kaoyan.assistant.recommendation;

import com.kaoyan.assistant.school.SchoolSummary;

import java.util.List;

public record RecommendationItem(
        SchoolSummary school,
        int matchScore,
        String groupTag,
        String riskLevel,
        Integer scoreGap,
        Integer benchmarkScore,
        int officialSourceCount,
        List<String> reasons
) {
}
