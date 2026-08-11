package com.kaoyan.assistant.major;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MajorRepository {

    private final JdbcTemplate jdbcTemplate;

    public MajorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MajorDto> findAll(Long schoolId, Long collegeId) {
        return jdbcTemplate.query("""
                SELECT id, school_id, college_id, name, major_code, degree_type,
                  research_direction, study_mode, remark
                FROM major
                WHERE (? IS NULL OR school_id = ?)
                  AND (? IS NULL OR college_id = ?)
                ORDER BY school_id ASC, college_id ASC, id ASC
                """, (rs, rowNum) -> new MajorDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getLong("college_id"),
                rs.getString("name"),
                rs.getString("major_code"),
                rs.getString("degree_type"),
                rs.getString("research_direction"),
                rs.getString("study_mode"),
                rs.getString("remark")
        ), schoolId, schoolId, collegeId, collegeId);
    }

    public MajorDto findById(Long id) {
        List<MajorDto> result = jdbcTemplate.query("""
                SELECT id, school_id, college_id, name, major_code, degree_type,
                  research_direction, study_mode, remark
                FROM major
                WHERE id = ?
                """, (rs, rowNum) -> new MajorDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getLong("college_id"),
                rs.getString("name"),
                rs.getString("major_code"),
                rs.getString("degree_type"),
                rs.getString("research_direction"),
                rs.getString("study_mode"),
                rs.getString("remark")
        ), id);
        return result.isEmpty() ? null : result.get(0);
    }

    public Long create(MajorRequest request) {
        jdbcTemplate.update("""
                INSERT INTO major (
                  school_id, college_id, name, major_code, degree_type,
                  research_direction, study_mode, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                request.schoolId(),
                request.collegeId(),
                request.name(),
                request.majorCode(),
                request.degreeType(),
                request.researchDirection(),
                request.studyMode(),
                request.remark());
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM major WHERE school_id = ? AND college_id = ? AND name = ?",
                Long.class, request.schoolId(), request.collegeId(), request.name());
    }

    public void update(Long id, MajorRequest request) {
        jdbcTemplate.update("""
                UPDATE major
                SET school_id = ?, college_id = ?, name = ?, major_code = ?, degree_type = ?,
                    research_direction = ?, study_mode = ?, remark = ?
                WHERE id = ?
                """,
                request.schoolId(),
                request.collegeId(),
                request.name(),
                request.majorCode(),
                request.degreeType(),
                request.researchDirection(),
                request.studyMode(),
                request.remark(),
                id);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM major WHERE id = ?", id);
    }
}
