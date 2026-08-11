package com.kaoyan.assistant.resultimport;

public record AdmissionResultImportPublishResult(
        Long batchId,
        String status,
        int admissionResultsCreated,
        int existingResults
) {
}
