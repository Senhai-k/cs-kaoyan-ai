package com.kaoyan.assistant.admission;

public record AdmissionPlanDto(
        Long id,
        Long schoolId,
        Long collegeId,
        Long majorId,
        Integer year,
        Integer totalQuota,
        Integer recommendedQuota,
        Integer unifiedQuota,
        boolean hasAdjustment,
        Long sourceId,
        String remark
) {
}
