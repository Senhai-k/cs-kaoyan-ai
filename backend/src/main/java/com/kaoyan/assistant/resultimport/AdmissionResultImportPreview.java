package com.kaoyan.assistant.resultimport;

import java.util.List;

public record AdmissionResultImportPreview(
        int inputRecords,
        int groupCount,
        int mappedGroupCount,
        boolean publishable,
        List<GroupPreview> groups
) {
    public record GroupPreview(
            String groupKey,
            String collegeName,
            String majorCode,
            String majorName,
            String degreeType,
            String studyMode,
            String candidateType,
            String specialProgram,
            int admittedCount,
            int scoreCoverageCount,
            Integer lowestScore,
            Double averageScore,
            Integer highestScore,
            Long collegeId,
            Long majorId,
            String mappingStatus,
            String mappingMessage
    ) {
    }
}
