package com.kaoyan.assistant.school;

public record AdjustmentInfoView(
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
