package com.kaoyan.assistant.adjustment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record AdjustmentInfoRequest(
        @NotNull Long schoolId,
        @NotNull Long collegeId,
        @NotNull Long majorId,
        @NotNull @Min(2000) @Max(2100) Integer year,
        String title,
        boolean open,
        @PositiveOrZero Integer vacancyCount,
        String applicationWindow,
        String requirements,
        String noticeUrl,
        Long sourceId,
        String remark
) {
}
