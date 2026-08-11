package com.kaoyan.assistant.score;

public record ScoreLineDto(
        Long id,
        Long schoolId,
        Long collegeId,
        Long majorId,
        Integer year,
        Integer totalScore,
        Integer politicsScore,
        Integer foreignLanguageScore,
        Integer mathScore,
        Integer professionalScore,
        Long sourceId,
        String remark
) {
}
