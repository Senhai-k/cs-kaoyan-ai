package com.kaoyan.assistant.quality;

import java.util.List;

public record DataCoverageReport(
        int schoolCount,
        int averageCoveragePercent,
        int readySchoolCount,
        int officialSourceCount,
        int officialDocumentCount,
        List<DataCoverageDimension> dimensions,
        List<SchoolCoverageItem> schools
) {
}
