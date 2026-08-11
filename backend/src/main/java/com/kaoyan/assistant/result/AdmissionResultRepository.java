package com.kaoyan.assistant.result;

import com.kaoyan.assistant.common.JdbcValues;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AdmissionResultRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdmissionResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdmissionResultDto> findAll(Long majorId) {
        return jdbcTemplate.query("""
                SELECT id, school_id, college_id, major_id, year, admitted_count, lowest_score,
                  average_score, highest_score, retest_ratio, source_id, remark
                FROM admission_result
                WHERE (? IS NULL OR major_id = ?)
                ORDER BY year DESC, id DESC
                """, (rs, rowNum) -> new AdmissionResultDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getLong("college_id"),
                rs.getLong("major_id"),
                (Integer) rs.getObject("year"),
                (Integer) rs.getObject("admitted_count"),
                (Integer) rs.getObject("lowest_score"),
                JdbcValues.nullableDouble(rs, "average_score"),
                (Integer) rs.getObject("highest_score"),
                JdbcValues.nullableDouble(rs, "retest_ratio"),
                (Long) rs.getObject("source_id"),
                rs.getString("remark")
        ), majorId, majorId);
    }

    public AdmissionResultDto findById(Long id) {
        List<AdmissionResultDto> result = jdbcTemplate.query("""
                SELECT id, school_id, college_id, major_id, year, admitted_count, lowest_score,
                  average_score, highest_score, retest_ratio, source_id, remark
                FROM admission_result
                WHERE id = ?
                """, (rs, rowNum) -> new AdmissionResultDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getLong("college_id"),
                rs.getLong("major_id"),
                (Integer) rs.getObject("year"),
                (Integer) rs.getObject("admitted_count"),
                (Integer) rs.getObject("lowest_score"),
                JdbcValues.nullableDouble(rs, "average_score"),
                (Integer) rs.getObject("highest_score"),
                JdbcValues.nullableDouble(rs, "retest_ratio"),
                (Long) rs.getObject("source_id"),
                rs.getString("remark")
        ), id);
        return result.isEmpty() ? null : result.get(0);
    }

    public Long create(AdmissionResultRequest request) {
        jdbcTemplate.update("""
                INSERT INTO admission_result (
                  school_id, college_id, major_id, year, admitted_count, lowest_score,
                  average_score, highest_score, retest_ratio, source_id, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                request.schoolId(), request.collegeId(), request.majorId(), request.year(),
                request.admittedCount(), request.lowestScore(), request.averageScore(),
                request.highestScore(), request.retestRatio(), request.sourceId(), request.remark());
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM admission_result WHERE major_id = ? AND year = ?",
                Long.class, request.majorId(), request.year()
        );
    }

    public void update(Long id, AdmissionResultRequest request) {
        jdbcTemplate.update("""
                UPDATE admission_result
                SET school_id = ?, college_id = ?, major_id = ?, year = ?, admitted_count = ?,
                    lowest_score = ?, average_score = ?, highest_score = ?, retest_ratio = ?,
                    source_id = ?, remark = ?
                WHERE id = ?
                """,
                request.schoolId(), request.collegeId(), request.majorId(), request.year(),
                request.admittedCount(), request.lowestScore(), request.averageScore(),
                request.highestScore(), request.retestRatio(), request.sourceId(), request.remark(), id);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM admission_result WHERE id = ?", id);
    }
}
