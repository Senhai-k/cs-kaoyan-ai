package com.kaoyan.assistant.school;

import jakarta.validation.constraints.NotBlank;

public record CreateSchoolRequest(
        @NotBlank String name,
        String province,
        String city,
        String region,
        String schoolLevel,
        boolean is985,
        boolean is211,
        boolean isDoubleFirstClass,
        String officialSite,
        String graduateSite
) {
}
