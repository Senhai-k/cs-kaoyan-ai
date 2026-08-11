package com.kaoyan.assistant.retest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RetestRuleRequest(
        @NotNull Long schoolId,
        Long collegeId,
        Long majorId,
        @NotNull @Min(2000) @Max(2100) Integer year,
        String retestTime,
        String retestMethod,
        @DecimalMin("0.0") @DecimalMax("10.0") Double retestRatio,
        @Min(0) @Max(100) Integer initialScoreWeight,
        @Min(0) @Max(100) Integer retestScoreWeight,
        String qualificationLine,
        String materials,
        Long sourceId,
        String remark
) {
}
