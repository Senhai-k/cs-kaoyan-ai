package com.kaoyan.assistant.school;

public record AdmissionPlanInfo(
        Integer year,
        Integer totalQuota,
        Integer recommendedQuota,
        Integer unifiedQuota,
        Boolean hasAdjustment,
        Long sourceId,
        String remark
) {
}
