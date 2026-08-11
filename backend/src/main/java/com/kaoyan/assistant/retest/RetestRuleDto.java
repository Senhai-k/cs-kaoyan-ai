package com.kaoyan.assistant.retest;

public record RetestRuleDto(
        Long id,
        Long schoolId,
        Long collegeId,
        Long majorId,
        String scopeType,
        Integer year,
        String retestTime,
        String retestMethod,
        Double retestRatio,
        Integer initialScoreWeight,
        Integer retestScoreWeight,
        String qualificationLine,
        String materials,
        Long sourceId,
        String remark
) {
}
