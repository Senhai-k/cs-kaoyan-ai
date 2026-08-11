package com.kaoyan.assistant.school;

public record NationalScoreLineInfo(
        Integer year,
        String categoryCode,
        String categoryName,
        String candidateType,
        Integer totalScore,
        Integer score100,
        Integer scoreOver100,
        boolean applicable,
        String sourceTitle,
        String sourceUrl,
        String publishedDate,
        String remark
) {
}
