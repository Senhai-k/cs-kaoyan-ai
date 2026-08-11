package com.kaoyan.assistant.compare;

import com.kaoyan.assistant.school.YearValue;

import java.util.List;

public record CompareSchoolItem(
        Long id,
        String name,
        String regionLabel,
        String schoolLevel,
        String collegeName,
        String majorName,
        String degreeType,
        String primarySubject,
        Boolean is408,
        Integer latestQuota,
        Integer latestScoreLine,
        List<YearValue> quotaHistory,
        List<YearValue> scoreLineHistory,
        int officialSourceCount,
        String latestSourceUpdatedAt
) {
}
