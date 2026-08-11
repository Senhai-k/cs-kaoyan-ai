package com.kaoyan.assistant.major;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MajorRequest(
        @NotNull Long schoolId,
        @NotNull Long collegeId,
        @NotBlank String name,
        String majorCode,
        String degreeType,
        String researchDirection,
        String studyMode,
        String remark
) {
}
