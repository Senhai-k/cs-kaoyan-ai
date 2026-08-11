package com.kaoyan.assistant.book;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReferenceBookRequest(
        @NotNull Long schoolId,
        @NotNull Long collegeId,
        @NotNull Long majorId,
        @NotNull @Min(2000) @Max(2100) Integer year,
        String subjectName,
        String bookTitle,
        String author,
        String edition,
        String publisher,
        Long sourceId,
        String remark
) {
}
