package com.kaoyan.assistant.exam;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ExamSubjectRepository {

    private final JdbcTemplate jdbcTemplate;

    public ExamSubjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ExamSubjectDto> findAll(Long majorId) {
        return jdbcTemplate.query("""
                SELECT id, school_id, college_id, major_id, year, politics, foreign_language,
                  math_subject, professional_subject, is_408, reference_books, source_id
                FROM exam_subject
                WHERE (? IS NULL OR major_id = ?)
                ORDER BY year DESC, id DESC
                """, (rs, rowNum) -> new ExamSubjectDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getLong("college_id"),
                rs.getLong("major_id"),
                (Integer) rs.getObject("year"),
                rs.getString("politics"),
                rs.getString("foreign_language"),
                rs.getString("math_subject"),
                rs.getString("professional_subject"),
                rs.getInt("is_408") == 1,
                rs.getString("reference_books"),
                (Long) rs.getObject("source_id")
        ), majorId, majorId);
    }

    public ExamSubjectDto findById(Long id) {
        List<ExamSubjectDto> result = jdbcTemplate.query("""
                SELECT id, school_id, college_id, major_id, year, politics, foreign_language,
                  math_subject, professional_subject, is_408, reference_books, source_id
                FROM exam_subject
                WHERE id = ?
                """, (rs, rowNum) -> new ExamSubjectDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getLong("college_id"),
                rs.getLong("major_id"),
                (Integer) rs.getObject("year"),
                rs.getString("politics"),
                rs.getString("foreign_language"),
                rs.getString("math_subject"),
                rs.getString("professional_subject"),
                rs.getInt("is_408") == 1,
                rs.getString("reference_books"),
                (Long) rs.getObject("source_id")
        ), id);
        return result.isEmpty() ? null : result.get(0);
    }

    public Long create(ExamSubjectRequest request) {
        jdbcTemplate.update("""
                INSERT INTO exam_subject (
                  school_id, college_id, major_id, year, politics, foreign_language,
                  math_subject, professional_subject, is_408, reference_books, source_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                request.schoolId(), request.collegeId(), request.majorId(), request.year(),
                request.politics(), request.foreignLanguage(), request.mathSubject(),
                request.professionalSubject(), request.is408() ? 1 : 0,
                request.referenceBooks(), request.sourceId());
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM exam_subject WHERE major_id = ? AND year = ?",
                Long.class, request.majorId(), request.year());
    }

    public void update(Long id, ExamSubjectRequest request) {
        jdbcTemplate.update("""
                UPDATE exam_subject
                SET school_id = ?, college_id = ?, major_id = ?, year = ?, politics = ?,
                    foreign_language = ?, math_subject = ?, professional_subject = ?,
                    is_408 = ?, reference_books = ?, source_id = ?
                WHERE id = ?
                """,
                request.schoolId(), request.collegeId(), request.majorId(), request.year(),
                request.politics(), request.foreignLanguage(), request.mathSubject(),
                request.professionalSubject(), request.is408() ? 1 : 0,
                request.referenceBooks(), request.sourceId(), id);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM exam_subject WHERE id = ?", id);
    }
}
