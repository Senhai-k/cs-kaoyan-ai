package com.kaoyan.assistant.catalog;

public record Catalog408ImportStatus(
        int year,
        boolean complete,
        int inputRecords,
        int schools,
        String retrievedAt,
        String importedAt,
        String sha256
) {
}
