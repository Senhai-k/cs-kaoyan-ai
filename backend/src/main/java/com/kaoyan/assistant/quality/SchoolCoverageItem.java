package com.kaoyan.assistant.quality;

import java.util.List;

public record SchoolCoverageItem(
        Long schoolId,
        String name,
        String province,
        String city,
        String schoolLevel,
        boolean selfDeterminedScore,
        String officialEntryUrl,
        int collegeCount,
        int majorCount,
        int examSubjectCount,
        int admissionPlanCount,
        int nationalBaselineCount,
        int schoolBaselineCount,
        int scoreLineCount,
        int admissionResultCount,
        int retestRuleCount,
        int referenceBookCount,
        int adjustmentInfoCount,
        int officialSourceCount,
        int officialDocumentCount,
        int coveragePercent,
        List<String> missingDimensions,
        String latestVerifiedAt
) {
}
