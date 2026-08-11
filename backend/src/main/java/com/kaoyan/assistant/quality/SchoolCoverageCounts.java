package com.kaoyan.assistant.quality;

record SchoolCoverageCounts(
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
        String latestSourceUpdatedAt,
        String latestDocumentUpdatedAt
) {
}
