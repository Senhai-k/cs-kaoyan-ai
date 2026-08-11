package com.kaoyan.assistant.score;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ScoreLineRequest(
        @NotNull Long schoolId,
        @NotNull Long collegeId,
        @NotNull Long majorId,
        @NotNull @Min(2000) @Max(2100) Integer year,
        @Min(0) @Max(500) Integer totalScore,
        @Min(0) @Max(100) Integer politicsScore,
        @Min(0) @Max(100) Integer foreignLanguageScore,
        @Min(0) @Max(150) Integer mathScore,
        @Min(0) @Max(150) Integer professionalScore,
        Long sourceId,
        String remark
) {
}
