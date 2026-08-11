package com.kaoyan.assistant.school;

public record SchoolProgramInfo(
        Long majorId,
        String collegeName,
        String majorName,
        String majorCode,
        String degreeType,
        String researchDirection,
        String studyMode,
        Integer year,
        String politics,
        String foreignLanguage,
        String mathSubject,
        String professionalSubject,
        boolean is408,
        Long sourceId
) {
}
