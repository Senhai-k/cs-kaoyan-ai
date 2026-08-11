package com.kaoyan.assistant.schoolscore;

import java.util.List;

public record SelfScoreLineImportRequest(
        Integer schemaVersion,
        Integer year,
        String publisher,
        String portalUrl,
        String retrievedAt,
        String reviewedAt,
        String sourceBatchSha256,
        String batchSha256,
        ImportStats stats,
        List<ReviewedLine> records
) {
    public record ImportStats(Integer schools, Integer available, Integer unavailable) {
    }

    public record ReviewedLine(
            String schoolName,
            String province,
            String city,
            String schoolLevel,
            Boolean is985,
            Boolean is211,
            Boolean isDoubleFirstClass,
            String title,
            String articleUrl,
            String publishedDate,
            String articleSha256,
            String imageUrl,
            String imageSha256,
            String categoryCode,
            String categoryName,
            String degreeType,
            Integer totalScore,
            Integer politicsScore,
            Integer foreignLanguageScore,
            Integer subjectOneScore,
            Integer subjectTwoScore,
            Integer score100,
            Integer scoreOver100,
            String availabilityStatus,
            String scopeNote,
            String remark
    ) {
    }
}
