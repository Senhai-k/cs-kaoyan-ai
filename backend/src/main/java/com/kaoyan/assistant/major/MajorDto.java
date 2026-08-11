package com.kaoyan.assistant.major;

public record MajorDto(
        Long id,
        Long schoolId,
        Long collegeId,
        String name,
        String majorCode,
        String degreeType,
        String researchDirection,
        String studyMode,
        String remark
) {
}
