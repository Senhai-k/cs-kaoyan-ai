package com.kaoyan.assistant.resultimport;

public record AdmissionResultImportBatchDto(
        Long id,
        Long schoolId,
        Integer year,
        Long sourceId,
        String sourceSha256,
        String batchSha256,
        String status,
        Integer inputRecords,
        Integer groupCount,
        Integer mappedGroupCount,
        String remark,
        String createdAt,
        String updatedAt,
        String publishedAt
) {
}
