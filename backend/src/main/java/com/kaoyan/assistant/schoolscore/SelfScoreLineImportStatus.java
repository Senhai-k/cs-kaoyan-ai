package com.kaoyan.assistant.schoolscore;

public record SelfScoreLineImportStatus(
        int year,
        int inputRecords,
        int schoolCount,
        String retrievedAt,
        String importedAt,
        String batchSha256
) {
}
