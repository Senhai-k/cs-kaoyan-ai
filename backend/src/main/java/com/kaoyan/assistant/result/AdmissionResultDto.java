package com.kaoyan.assistant.result;

public record AdmissionResultDto(
        Long id,
        Long schoolId,
        Long collegeId,
        Long majorId,
        Integer year,
        Integer admittedCount,
        Integer lowestScore,
        Double averageScore,
        Integer highestScore,
        Double retestRatio,
        Long sourceId,
        String remark
) {
}
