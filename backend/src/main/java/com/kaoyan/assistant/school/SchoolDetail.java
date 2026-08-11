package com.kaoyan.assistant.school;

import com.kaoyan.assistant.schoolscore.SchoolScoreLineInfo;

import java.util.List;

public record SchoolDetail(
        SchoolSummary summary,
        String collegeName,
        String majorName,
        String majorCode,
        String degreeType,
        String researchDirection,
        String studyMode,
        List<SchoolProgramInfo> programs,
        Long examSourceId,
        List<YearValue> quotas,
        List<AdmissionPlanInfo> admissionPlans,
        List<YearValue> scoreLines,
        List<NationalScoreLineInfo> nationalScoreLines,
        List<SchoolScoreLineInfo> schoolScoreLines,
        List<AdmissionResultInfo> admissionResults,
        List<RetestRuleInfo> retestRules,
        List<ReferenceBookInfo> referenceBooks,
        List<AdjustmentInfoView> adjustmentInfos,
        List<SourceInfo> sources
) {
}
