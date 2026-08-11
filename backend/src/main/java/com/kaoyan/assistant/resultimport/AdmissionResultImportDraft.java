package com.kaoyan.assistant.resultimport;

public record AdmissionResultImportDraft(
        AdmissionResultImportBatchDto batch,
        AdmissionResultImportPreview preview,
        boolean existing
) {
}
