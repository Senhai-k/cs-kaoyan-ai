package com.kaoyan.assistant.score;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ScoreLineRepository {

    private final JdbcTemplate jdbcTemplate;

    public ScoreLineRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ScoreLineDto> findAll(Long majorId) {
        return jdbcTemplate.query("""
                SELECT id, school_id, college_id, major_id, year, total_score, politics_score,
                  foreign_language_score, math_score, professional_score, source_id, remark
                FROM score_line
                WHERE (? IS NULL OR major_id = ?)
                ORDER BY year DESC, id DESC
                """, (rs, rowNum) -> new ScoreLineDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getLong("college_id"),
                rs.getLong("major_id"),
                (Integer) rs.getObject("year"),
                (Integer) rs.getObject("total_score"),
                (Integer) rs.getObject("politics_score"),
                (Integer) rs.getObject("foreign_language_score"),
                (Integer) rs.getObject("math_score"),
                (Integer) rs.getObject("professional_score"),
                (Long) rs.getObject("source_id"),
                rs.getString("remark")
        ), majorId, majorId);
    }

    public ScoreLineDto findById(Long id) {
        List<ScoreLineDto> result = jdbcTemplate.query("""
                SELECT id, school_id, college_id, major_id, year, total_score, politics_score,
                  foreign_language_score, math_score, professional_score, source_id, remark
                FROM score_line
                WHERE id = ?
                """, (rs, rowNum) -> new ScoreLineDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getLong("college_id"),
                rs.getLong("major_id"),
                (Integer) rs.getObject("year"),
                (Integer) rs.getObject("total_score"),
                (Integer) rs.getObject("politics_score"),
                (Integer) rs.getObject("foreign_language_score"),
                (Integer) rs.getObject("math_score"),
                (Integer) rs.getObject("professional_score"),
                (Long) rs.getObject("source_id"),
                rs.getString("remark")
        ), id);
        return result.isEmpty() ? null : result.get(0);
    }

    public Long create(ScoreLineRequest request) {
        jdbcTemplate.update("""
                INSERT INTO score_line (
                  school_id, college_id, major_id, year, total_score, politics_score,
                  foreign_language_score, math_score, professional_score, source_id, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                request.schoolId(), request.collegeId(), request.majorId(), request.year(),
                request.totalScore(), request.politicsScore(), request.foreignLanguageScore(),
                request.mathScore(), request.professionalScore(), request.sourceId(), request.remark());
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM score_line WHERE major_id = ? AND year = ?",
                Long.class, request.majorId(), request.year());
    }

    public void update(Long id, ScoreLineRequest request) {
        jdbcTemplate.update("""
                UPDATE score_line
                SET school_id = ?, college_id = ?, major_id = ?, year = ?, total_score = ?,
                    politics_score = ?, foreign_language_score = ?, math_score = ?,
                    professional_score = ?, source_id = ?, remark = ?
                WHERE id = ?
                """,
                request.schoolId(), request.collegeId(), request.majorId(), request.year(),
                request.totalScore(), request.politicsScore(), request.foreignLanguageScore(),
                request.mathScore(), request.professionalScore(), request.sourceId(), request.remark(), id);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM score_line WHERE id = ?", id);
    }
}
