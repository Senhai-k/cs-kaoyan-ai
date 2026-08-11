package com.kaoyan.assistant.school;

public record ReferenceBookInfo(
        Integer year,
        String subjectName,
        String bookTitle,
        String author,
        String edition,
        String publisher,
        Long sourceId,
        String remark
) {
}
