package com.kaoyan.assistant.school;

public record AdmissionResultInfo(
        Integer year,
        Integer admittedCount,
        Integer lowestScore,
        Double averageScore,
        Integer highestScore,
        Double retestRatio,
        Long sourceId
) {
}
