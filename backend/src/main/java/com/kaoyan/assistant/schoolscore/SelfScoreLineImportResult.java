package com.kaoyan.assistant.schoolscore;

public record SelfScoreLineImportResult(
        int year,
        int inputRecords,
        int schools,
        int available,
        int unavailable,
        int schoolsCreated,
        int scoreLinesCreated,
        int sourcesCreated,
        int documentsCreated,
        int existingRecords
) {
}
