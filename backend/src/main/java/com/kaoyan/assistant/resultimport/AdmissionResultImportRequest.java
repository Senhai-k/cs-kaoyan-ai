package com.kaoyan.assistant.resultimport;

import java.util.List;

public record AdmissionResultImportRequest(
        Integer schemaVersion,
        Long schoolId,
        Integer year,
        String documentType,
        Long sourceId,
        String sourceSha256,
        String batchSha256,
        String remark,
        List<CandidateRecord> records
) {
    public record CandidateRecord(
            String candidateKey,
            String collegeName,
            String majorCode,
            String majorName,
            String degreeType,
            String studyMode,
            String candidateType,
            Integer initialScore,
            Double retestScore,
            Double finalScore,
            String specialProgram
    ) {
    }
}
