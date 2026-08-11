package com.kaoyan.assistant.college;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CollegeRepository {

    private final JdbcTemplate jdbcTemplate;

    public CollegeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CollegeDto> findAll(Long schoolId) {
        String sql = """
                SELECT id, school_id, name, official_site, remark
                FROM college
                WHERE (? IS NULL OR school_id = ?)
                ORDER BY school_id ASC, id ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CollegeDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getString("name"),
                rs.getString("official_site"),
                rs.getString("remark")
        ), schoolId, schoolId);
    }

    public CollegeDto findById(Long id) {
        List<CollegeDto> result = jdbcTemplate.query("""
                SELECT id, school_id, name, official_site, remark
                FROM college
                WHERE id = ?
                """, (rs, rowNum) -> new CollegeDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getString("name"),
                rs.getString("official_site"),
                rs.getString("remark")
        ), id);
        return result.isEmpty() ? null : result.get(0);
    }

    public Long create(CollegeRequest request) {
        jdbcTemplate.update("""
                INSERT INTO college (school_id, name, official_site, remark)
                VALUES (?, ?, ?, ?)
                """, request.schoolId(), request.name(), request.officialSite(), request.remark());
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM college WHERE school_id = ? AND name = ?",
                Long.class, request.schoolId(), request.name());
    }

    public void update(Long id, CollegeRequest request) {
        jdbcTemplate.update("""
                UPDATE college
                SET school_id = ?, name = ?, official_site = ?, remark = ?
                WHERE id = ?
                """, request.schoolId(), request.name(), request.officialSite(), request.remark(), id);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM college WHERE id = ?", id);
    }
}
