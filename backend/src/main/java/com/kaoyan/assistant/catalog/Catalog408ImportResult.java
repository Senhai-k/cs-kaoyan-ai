package com.kaoyan.assistant.catalog;

public record Catalog408ImportResult(
        int year,
        boolean complete,
        int inputRecords,
        int schoolsCreated,
        int collegesCreated,
        int majorsCreated,
        int sourcesCreated,
        int documentsCreated,
        int examSubjectsCreated,
        int admissionPlansCreated,
        int retestRulesCreated,
        int existingRecords
) {
}
