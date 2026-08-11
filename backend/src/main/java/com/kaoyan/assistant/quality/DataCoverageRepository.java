package com.kaoyan.assistant.quality;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DataCoverageRepository {

    private final JdbcTemplate jdbcTemplate;

    public DataCoverageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SchoolCoverageCounts> findSchoolCoverageCounts() {
        return jdbcTemplate.query("""
                SELECT
                  s.id,
                  s.name,
                  s.province,
                  s.city,
                  s.school_level,
                  s.is_self_determined_score,
                  COALESCE(NULLIF(s.graduate_site, ''), s.official_site) AS official_entry_url,
                  (SELECT COUNT(*) FROM college c WHERE c.school_id = s.id) AS college_count,
                  (SELECT COUNT(*) FROM major m WHERE m.school_id = s.id) AS major_count,
                  (SELECT COUNT(*) FROM exam_subject es WHERE es.school_id = s.id AND es.source_id IS NOT NULL) AS exam_subject_count,
                  (SELECT COUNT(*) FROM admission_plan ap WHERE ap.school_id = s.id AND ap.source_id IS NOT NULL) AS admission_plan_count,
                  (SELECT COUNT(*) FROM major baseline_major
                    JOIN national_score_line nsl
                      ON nsl.category_code = CASE
                        WHEN baseline_major.major_code LIKE '07%' THEN '07'
                        WHEN baseline_major.major_code LIKE '14%' THEN '14'
                        ELSE '08'
                      END
                     AND nsl.candidate_type = CASE WHEN s.province IN
                       ('内蒙古', '广西', '海南', '贵州', '云南', '西藏', '甘肃', '青海', '宁夏', '新疆')
                       THEN 'B' ELSE 'A' END
                    WHERE baseline_major.school_id = s.id) AS national_baseline_count,
                  (SELECT COUNT(*) FROM school_score_line school_line
                    WHERE school_line.school_id = s.id
                      AND school_line.availability_status = 'AVAILABLE') AS school_baseline_count,
                  (SELECT COUNT(*) FROM score_line sl WHERE sl.school_id = s.id AND sl.source_id IS NOT NULL) AS score_line_count,
                  (SELECT COUNT(*) FROM admission_result ar WHERE ar.school_id = s.id AND ar.source_id IS NOT NULL) AS admission_result_count,
                  (SELECT COUNT(*) FROM retest_rule rr WHERE rr.school_id = s.id AND rr.source_id IS NOT NULL) AS retest_rule_count,
                  (SELECT COUNT(*) FROM reference_book rb WHERE rb.school_id = s.id AND rb.source_id IS NOT NULL) AS reference_book_count,
                  (SELECT COUNT(*) FROM adjustment_info ai WHERE ai.school_id = s.id AND ai.source_id IS NOT NULL) AS adjustment_info_count,
                  (SELECT COUNT(*) FROM document_source ds
                    WHERE ds.school_id = s.id AND ds.is_official = 1 AND ds.audit_status = 'PUBLISHED') AS official_source_count,
                  (SELECT COUNT(*) FROM source_document sd
                    WHERE sd.school_id = s.id AND sd.source_reliability = 'OFFICIAL' AND sd.audit_status = 'PUBLISHED') AS official_document_count,
                  (SELECT MAX(ds.updated_at) FROM document_source ds
                    WHERE ds.school_id = s.id AND ds.is_official = 1 AND ds.audit_status = 'PUBLISHED') AS latest_source_updated_at,
                  (SELECT MAX(sd.updated_at) FROM source_document sd
                    WHERE sd.school_id = s.id AND sd.source_reliability = 'OFFICIAL' AND sd.audit_status = 'PUBLISHED') AS latest_document_updated_at
                FROM school s
                ORDER BY s.id
                """, (rs, rowNum) -> new SchoolCoverageCounts(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("province"),
                rs.getString("city"),
                rs.getString("school_level"),
                rs.getInt("is_self_determined_score") == 1,
                rs.getString("official_entry_url"),
                rs.getInt("college_count"),
                rs.getInt("major_count"),
                rs.getInt("exam_subject_count"),
                rs.getInt("admission_plan_count"),
                rs.getInt("national_baseline_count"),
                rs.getInt("school_baseline_count"),
                rs.getInt("score_line_count"),
                rs.getInt("admission_result_count"),
                rs.getInt("retest_rule_count"),
                rs.getInt("reference_book_count"),
                rs.getInt("adjustment_info_count"),
                rs.getInt("official_source_count"),
                rs.getInt("official_document_count"),
                rs.getString("latest_source_updated_at"),
                rs.getString("latest_document_updated_at")
        ));
    }
}
