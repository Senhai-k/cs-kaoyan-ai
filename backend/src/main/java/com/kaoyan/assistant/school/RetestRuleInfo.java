package com.kaoyan.assistant.school;

public record RetestRuleInfo(
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
