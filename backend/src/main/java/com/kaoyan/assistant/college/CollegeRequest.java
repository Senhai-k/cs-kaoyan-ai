package com.kaoyan.assistant.college;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CollegeRequest(
        @NotNull Long schoolId,
        @NotBlank String name,
        String officialSite,
        String remark
) {
}
