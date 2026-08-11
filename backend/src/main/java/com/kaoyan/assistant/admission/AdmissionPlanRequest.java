package com.kaoyan.assistant.admission;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record AdmissionPlanRequest(
        @NotNull Long schoolId,
        @NotNull Long collegeId,
        @NotNull Long majorId,
        @NotNull @Min(2000) @Max(2100) Integer year,
        @PositiveOrZero Integer totalQuota,
        @PositiveOrZero Integer recommendedQuota,
        @PositiveOrZero Integer unifiedQuota,
        boolean hasAdjustment,
        Long sourceId,
        String remark
) {
}
