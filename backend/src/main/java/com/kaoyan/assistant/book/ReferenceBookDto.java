package com.kaoyan.assistant.book;

public record ReferenceBookDto(
        Long id,
        Long schoolId,
        Long collegeId,
        Long majorId,
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
