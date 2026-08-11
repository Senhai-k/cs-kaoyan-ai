package com.kaoyan.assistant.adjustment;

public record AdjustmentInfoDto(
        Long id,
        Long schoolId,
        Long collegeId,
        Long majorId,
        Integer year,
        String title,
        boolean open,
        Integer vacancyCount,
        String applicationWindow,
        String requirements,
        String noticeUrl,
        Long sourceId,
        String remark
) {
}
