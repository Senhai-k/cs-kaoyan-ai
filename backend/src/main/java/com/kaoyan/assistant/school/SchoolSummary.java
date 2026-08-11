package com.kaoyan.assistant.school;

public record SchoolSummary(
        Long id,
        String name,
        String province,
        String city,
        String schoolLevel,
        boolean is985,
        boolean is211,
        boolean isDoubleFirstClass,
        String primarySubject,
        Boolean is408,
        Integer latestQuota,
        Integer latestScoreLine
) {
}
