package com.kaoyan.assistant.book;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ReferenceBookRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReferenceBookRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReferenceBookDto> findAll(Long majorId) {
        return jdbcTemplate.query("""
                SELECT id, school_id, college_id, major_id, year, subject_name, book_title, author,
                  edition, publisher, source_id, remark
                FROM reference_book
                WHERE (? IS NULL OR major_id = ?)
                ORDER BY year DESC, id DESC
                """, (rs, rowNum) -> new ReferenceBookDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getLong("college_id"),
                rs.getLong("major_id"),
                (Integer) rs.getObject("year"),
                rs.getString("subject_name"),
                rs.getString("book_title"),
                rs.getString("author"),
                rs.getString("edition"),
                rs.getString("publisher"),
                (Long) rs.getObject("source_id"),
                rs.getString("remark")
        ), majorId, majorId);
    }

    public ReferenceBookDto findById(Long id) {
        List<ReferenceBookDto> result = jdbcTemplate.query("""
                SELECT id, school_id, college_id, major_id, year, subject_name, book_title, author,
                  edition, publisher, source_id, remark
                FROM reference_book
                WHERE id = ?
                """, (rs, rowNum) -> new ReferenceBookDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getLong("college_id"),
                rs.getLong("major_id"),
                (Integer) rs.getObject("year"),
                rs.getString("subject_name"),
                rs.getString("book_title"),
                rs.getString("author"),
                rs.getString("edition"),
                rs.getString("publisher"),
                (Long) rs.getObject("source_id"),
                rs.getString("remark")
        ), id);
        return result.isEmpty() ? null : result.get(0);
    }

    public Long create(ReferenceBookRequest request) {
        jdbcTemplate.update("""
                INSERT INTO reference_book (
                  school_id, college_id, major_id, year, subject_name, book_title,
                  author, edition, publisher, source_id, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                request.schoolId(), request.collegeId(), request.majorId(), request.year(),
                request.subjectName(), request.bookTitle(), request.author(), request.edition(),
                request.publisher(), request.sourceId(), request.remark());
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM reference_book WHERE major_id = ? AND year = ?",
                Long.class, request.majorId(), request.year()
        );
    }

    public void update(Long id, ReferenceBookRequest request) {
        jdbcTemplate.update("""
                UPDATE reference_book
                SET school_id = ?, college_id = ?, major_id = ?, year = ?, subject_name = ?,
                    book_title = ?, author = ?, edition = ?, publisher = ?, source_id = ?, remark = ?
                WHERE id = ?
                """,
                request.schoolId(), request.collegeId(), request.majorId(), request.year(),
                request.subjectName(), request.bookTitle(), request.author(), request.edition(),
                request.publisher(), request.sourceId(), request.remark(), id);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM reference_book WHERE id = ?", id);
    }
}
