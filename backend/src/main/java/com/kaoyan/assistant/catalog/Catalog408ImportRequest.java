package com.kaoyan.assistant.catalog;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record Catalog408ImportRequest(
        Integer schemaVersion,
        String collectorVersion,
        Integer year,
        String retrievedAt,
        CatalogStats stats,
        String sha256,
        List<CatalogRecord> records
) {
    public record CatalogStats(boolean complete, Integer professionalEntries, Integer schoolsVisited,
                               Integer raw408Directions, Integer records, Integer schools) {
    }

    public record CatalogRecord(School school, College college, Major major, Subjects subjects,
                                Source source, List<Direction> directions, List<String> quotaTexts,
                                List<String> majorRemarks, List<String> catalogRecordIds) {
    }

    public record School(String code, String chsiId, String name, String provinceCode, String province,
                         boolean is985, boolean is211, boolean isDoubleFirstClass) {
    }

    public record College(String code, String name) {
    }

    public record Major(String code, String name, String degreeType, String studyMode) {
    }

    public record Direction(String code, String name) {
    }

    public record Subjects(Subject politics, Subject foreignLanguage, Subject math, Subject professional) {
    }

    public record Subject(String code, String name, String note) {
    }

    public record Source(String title, String type, String url, boolean official, String publisher,
                         JsonNode rawEvidence, String sha256) {
    }
}
