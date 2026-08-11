package com.kaoyan.assistant.exam;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ExamSubjectRequest(
        @NotNull Long schoolId,
        @NotNull Long collegeId,
        @NotNull Long majorId,
        @NotNull @Min(2000) @Max(2100) Integer year,
        String politics,
        String foreignLanguage,
        String mathSubject,
        String professionalSubject,
        boolean is408,
        String referenceBooks,
        Long sourceId
) {
}
