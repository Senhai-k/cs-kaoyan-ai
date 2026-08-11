package com.kaoyan.assistant.exam;

public record ExamSubjectDto(
        Long id,
        Long schoolId,
        Long collegeId,
        Long majorId,
        Integer year,
        String politics,
        String foreignLanguage,
        String mathSubject,
        String professionalSubject,
        boolean is408,
        String referenceBooks,
        Long sourceId
) {
}
