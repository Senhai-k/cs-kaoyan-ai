package com.kaoyan.assistant.schoolscore;

public record SchoolScoreLineInfo(
        Integer year,
        String categoryCode,
        String categoryName,
        String degreeType,
        Integer totalScore,
        Integer politicsScore,
        Integer foreignLanguageScore,
        Integer subjectOneScore,
        Integer subjectTwoScore,
        Integer score100,
        Integer scoreOver100,
        String availabilityStatus,
        Long sourceId,
        String sourceTitle,
        String articleUrl,
        String imageUrl,
        String publishedDate,
        String scopeNote,
        String remark
) {
}
