package com.kaoyan.assistant.retest;

import com.kaoyan.assistant.common.JdbcValues;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class RetestRuleRepository {

    private final JdbcTemplate jdbcTemplate;

    public RetestRuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RetestRuleDto> findAll(Long majorId) {
        return jdbcTemplate.query("""
                SELECT id, school_id, college_id, major_id, year, retest_time, retest_method,
                  retest_ratio, initial_score_weight, retest_score_weight, qualification_line,
                  materials, source_id, remark
                FROM retest_rule
                WHERE (? IS NULL OR major_id = ?
                  OR (major_id IS NULL AND college_id = (SELECT college_id FROM major WHERE id = ?))
                  OR (major_id IS NULL AND college_id IS NULL
                    AND school_id = (SELECT school_id FROM major WHERE id = ?)))
                ORDER BY year DESC,
                  CASE WHEN major_id IS NOT NULL THEN 0 WHEN college_id IS NOT NULL THEN 1 ELSE 2 END,
                  id DESC
                """, this::map, majorId, majorId, majorId, majorId);
    }

    public RetestRuleDto findById(Long id) {
        List<RetestRuleDto> result = jdbcTemplate.query("""
                SELECT id, school_id, college_id, major_id, year, retest_time, retest_method,
                  retest_ratio, initial_score_weight, retest_score_weight, qualification_line,
                  materials, source_id, remark
                FROM retest_rule
                WHERE id = ?
                """, this::map, id);
        return result.isEmpty() ? null : result.get(0);
    }

    public Long create(RetestRuleRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO retest_rule (
                      school_id, college_id, major_id, year, retest_time, retest_method,
                      retest_ratio, initial_score_weight, retest_score_weight, qualification_line,
                      materials, source_id, remark
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setObject(1, request.schoolId());
            statement.setObject(2, request.collegeId());
            statement.setObject(3, request.majorId());
            statement.setObject(4, request.year());
            statement.setObject(5, request.retestTime());
            statement.setObject(6, request.retestMethod());
            statement.setObject(7, request.retestRatio());
            statement.setObject(8, request.initialScoreWeight());
            statement.setObject(9, request.retestScoreWeight());
            statement.setObject(10, request.qualificationLine());
            statement.setObject(11, request.materials());
            statement.setObject(12, request.sourceId());
            statement.setObject(13, request.remark());
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void update(Long id, RetestRuleRequest request) {
        jdbcTemplate.update("""
                UPDATE retest_rule
                SET school_id = ?, college_id = ?, major_id = ?, year = ?, retest_time = ?,
                    retest_method = ?, retest_ratio = ?, initial_score_weight = ?,
                    retest_score_weight = ?, qualification_line = ?, materials = ?,
                    source_id = ?, remark = ?
                WHERE id = ?
                """,
                request.schoolId(), request.collegeId(), request.majorId(), request.year(),
                request.retestTime(), request.retestMethod(), request.retestRatio(),
                request.initialScoreWeight(), request.retestScoreWeight(),
                request.qualificationLine(), request.materials(),
                request.sourceId(), request.remark(), id);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM retest_rule WHERE id = ?", id);
    }

    public boolean scopeBelongsToSchool(Long schoolId, Long collegeId, Long majorId) {
        if (majorId != null) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM major
                    WHERE id = ? AND school_id = ? AND college_id = ?
                    """, Integer.class, majorId, schoolId, collegeId);
            return count != null && count > 0;
        }
        if (collegeId != null) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM college WHERE id = ? AND school_id = ?",
                    Integer.class, collegeId, schoolId
            );
            return count != null && count > 0;
        }
        return true;
    }

    private RetestRuleDto map(ResultSet rs, int rowNum) throws SQLException {
        Long collegeId = (Long) rs.getObject("college_id");
        Long majorId = (Long) rs.getObject("major_id");
        String scopeType = majorId != null ? "MAJOR" : collegeId != null ? "COLLEGE" : "SCHOOL";
        return new RetestRuleDto(
                rs.getLong("id"), rs.getLong("school_id"), collegeId, majorId, scopeType,
                (Integer) rs.getObject("year"), rs.getString("retest_time"), rs.getString("retest_method"),
                JdbcValues.nullableDouble(rs, "retest_ratio"),
                (Integer) rs.getObject("initial_score_weight"),
                (Integer) rs.getObject("retest_score_weight"),
                rs.getString("qualification_line"), rs.getString("materials"),
                (Long) rs.getObject("source_id"), rs.getString("remark")
        );
    }
}
