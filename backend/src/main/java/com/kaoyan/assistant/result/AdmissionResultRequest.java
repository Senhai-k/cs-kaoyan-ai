package com.kaoyan.assistant.result;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record AdmissionResultRequest(
        @NotNull Long schoolId,
        @NotNull Long collegeId,
        @NotNull Long majorId,
        @NotNull @Min(2000) @Max(2100) Integer year,
        @PositiveOrZero Integer admittedCount,
        @Min(0) @Max(500) Integer lowestScore,
        @DecimalMin("0.0") @DecimalMax("500.0") Double averageScore,
        @Min(0) @Max(500) Integer highestScore,
        @DecimalMin("0.0") @DecimalMax("10.0") Double retestRatio,
        Long sourceId,
        String remark
) {
}
