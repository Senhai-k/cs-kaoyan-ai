package com.kaoyan.assistant.adjustment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AdjustmentInfoRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdjustmentInfoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdjustmentInfoDto> findAll(Long majorId) {
        return jdbcTemplate.query("""
                SELECT id, school_id, college_id, major_id, year, title, is_open, vacancy_count,
                  application_window, requirements, notice_url, source_id, remark
                FROM adjustment_info
                WHERE (? IS NULL OR major_id = ?)
                ORDER BY year DESC, id DESC
                """, (rs, rowNum) -> new AdjustmentInfoDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getLong("college_id"),
                rs.getLong("major_id"),
                (Integer) rs.getObject("year"),
                rs.getString("title"),
                rs.getInt("is_open") == 1,
                (Integer) rs.getObject("vacancy_count"),
                rs.getString("application_window"),
                rs.getString("requirements"),
                rs.getString("notice_url"),
                (Long) rs.getObject("source_id"),
                rs.getString("remark")
        ), majorId, majorId);
    }

    public AdjustmentInfoDto findById(Long id) {
        List<AdjustmentInfoDto> result = jdbcTemplate.query("""
                SELECT id, school_id, college_id, major_id, year, title, is_open, vacancy_count,
                  application_window, requirements, notice_url, source_id, remark
                FROM adjustment_info
                WHERE id = ?
                """, (rs, rowNum) -> new AdjustmentInfoDto(
                rs.getLong("id"),
                rs.getLong("school_id"),
                rs.getLong("college_id"),
                rs.getLong("major_id"),
                (Integer) rs.getObject("year"),
                rs.getString("title"),
                rs.getInt("is_open") == 1,
                (Integer) rs.getObject("vacancy_count"),
                rs.getString("application_window"),
                rs.getString("requirements"),
                rs.getString("notice_url"),
                (Long) rs.getObject("source_id"),
                rs.getString("remark")
        ), id);
        return result.isEmpty() ? null : result.get(0);
    }

    public Long create(AdjustmentInfoRequest request) {
        jdbcTemplate.update("""
                INSERT INTO adjustment_info (
                  school_id, college_id, major_id, year, title, is_open, vacancy_count,
                  application_window, requirements, notice_url, source_id, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                request.schoolId(), request.collegeId(), request.majorId(), request.year(),
                request.title(), request.open() ? 1 : 0, request.vacancyCount(),
                request.applicationWindow(), request.requirements(), request.noticeUrl(),
                request.sourceId(), request.remark());
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM adjustment_info WHERE major_id = ? AND year = ?",
                Long.class, request.majorId(), request.year()
        );
    }

    public void update(Long id, AdjustmentInfoRequest request) {
        jdbcTemplate.update("""
                UPDATE adjustment_info
                SET school_id = ?, college_id = ?, major_id = ?, year = ?, title = ?,
                    is_open = ?, vacancy_count = ?, application_window = ?, requirements = ?,
                    notice_url = ?, source_id = ?, remark = ?
                WHERE id = ?
                """,
                request.schoolId(), request.collegeId(), request.majorId(), request.year(),
                request.title(), request.open() ? 1 : 0, request.vacancyCount(),
                request.applicationWindow(), request.requirements(), request.noticeUrl(),
                request.sourceId(), request.remark(), id);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM adjustment_info WHERE id = ?", id);
    }
}
