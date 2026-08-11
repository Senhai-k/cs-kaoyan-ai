package com.kaoyan.assistant.admission;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AdmissionPlanRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdmissionPlanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdmissionPlanDto> findAll(Long majorId) {
        return jdbcTemplate.query("""
                SELECT id, school_id, college_id, major_id, year, total_quota,
                  recommended_quota, unified_quota, has_adjustment, source_id, remark
                FROM admission_plan
                WHERE (? IS NULL OR major_id = ?)
                ORDER BY year DESC, id DESC
                """, (rs, rowNum) -> new AdmissionPlanDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getLong("college_id"),
                rs.getLong("major_id"),
                (Integer) rs.getObject("year"),
                (Integer) rs.getObject("total_quota"),
                (Integer) rs.getObject("recommended_quota"),
                (Integer) rs.getObject("unified_quota"),
                rs.getInt("has_adjustment") == 1,
                (Long) rs.getObject("source_id"),
                rs.getString("remark")
        ), majorId, majorId);
    }

    public AdmissionPlanDto findById(Long id) {
        List<AdmissionPlanDto> result = jdbcTemplate.query("""
                SELECT id, school_id, college_id, major_id, year, total_quota,
                  recommended_quota, unified_quota, has_adjustment, source_id, remark
                FROM admission_plan
                WHERE id = ?
                """, (rs, rowNum) -> new AdmissionPlanDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getLong("college_id"),
                rs.getLong("major_id"),
                (Integer) rs.getObject("year"),
                (Integer) rs.getObject("total_quota"),
                (Integer) rs.getObject("recommended_quota"),
                (Integer) rs.getObject("unified_quota"),
                rs.getInt("has_adjustment") == 1,
                (Long) rs.getObject("source_id"),
                rs.getString("remark")
        ), id);
        return result.isEmpty() ? null : result.get(0);
    }

    public Long create(AdmissionPlanRequest request) {
        jdbcTemplate.update("""
                INSERT INTO admission_plan (
                  school_id, college_id, major_id, year, total_quota, recommended_quota,
                  unified_quota, has_adjustment, source_id, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                request.schoolId(), request.collegeId(), request.majorId(), request.year(),
                request.totalQuota(), request.recommendedQuota(), request.unifiedQuota(),
                request.hasAdjustment() ? 1 : 0, request.sourceId(), request.remark());
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM admission_plan WHERE major_id = ? AND year = ?",
                Long.class, request.majorId(), request.year());
    }

    public void update(Long id, AdmissionPlanRequest request) {
        jdbcTemplate.update("""
                UPDATE admission_plan
                SET school_id = ?, college_id = ?, major_id = ?, year = ?, total_quota = ?,
                    recommended_quota = ?, unified_quota = ?, has_adjustment = ?,
                    source_id = ?, remark = ?
                WHERE id = ?
                """,
                request.schoolId(), request.collegeId(), request.majorId(), request.year(),
                request.totalQuota(), request.recommendedQuota(), request.unifiedQuota(),
                request.hasAdjustment() ? 1 : 0, request.sourceId(), request.remark(), id);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM admission_plan WHERE id = ?", id);
    }
}
