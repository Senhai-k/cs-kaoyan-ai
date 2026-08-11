package com.kaoyan.assistant.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Repository
public class Catalog408ImportRepository {

    private static final String CATALOG_PLAN_MARKER = "研招网目录自动结构化｜";
    private static final String LEGACY_CATALOG_PLAN_MARKER = "[CHSI_408_CATALOG]";
    private static final String CATALOG_RETEST_MARKER = "研招网目录复试信息自动结构化｜";
    private static final Set<String> SELF_DETERMINED_SCORE_SCHOOLS = Set.of(
            "北京大学", "清华大学", "中国人民大学", "北京师范大学", "北京航空航天大学", "北京理工大学", "中国农业大学",
            "南开大学", "天津大学", "大连理工大学", "东北大学", "吉林大学", "哈尔滨工业大学", "复旦大学", "同济大学",
            "上海交通大学", "南京大学", "东南大学", "浙江大学", "中国科学技术大学", "厦门大学", "山东大学", "武汉大学",
            "华中科技大学", "湖南大学", "中南大学", "中山大学", "华南理工大学", "四川大学", "重庆大学", "电子科技大学",
            "西安交通大学", "西北工业大学", "兰州大学"
    );

    private final JdbcTemplate jdbcTemplate;

    public Catalog408ImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveBatch(Catalog408ImportRequest request) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM catalog_import_batch
                WHERE catalog_type = 'CHSI_408' AND year = ? AND batch_sha256 = ?
                """, Integer.class, request.year(), request.sha256());
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE catalog_import_batch
                    SET retrieved_at = ?, is_complete = ?, input_records = ?, school_count = ?,
                        imported_at = CURRENT_TIMESTAMP
                    WHERE catalog_type = 'CHSI_408' AND year = ? AND batch_sha256 = ?
                    """, request.retrievedAt(), request.stats().complete() ? 1 : 0, request.records().size(),
                    request.stats().schools(), request.year(), request.sha256());
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO catalog_import_batch (catalog_type, year, retrieved_at, is_complete,
                  input_records, school_count, batch_sha256)
                VALUES ('CHSI_408', ?, ?, ?, ?, ?, ?)
                """, request.year(), request.retrievedAt(), request.stats().complete() ? 1 : 0,
                request.records().size(), request.stats().schools(), request.sha256());
    }

    public Catalog408ImportStatus findLatestBatch() {
        List<Catalog408ImportStatus> result = jdbcTemplate.query("""
                SELECT year, is_complete, input_records, school_count, retrieved_at, imported_at, batch_sha256
                FROM catalog_import_batch
                WHERE catalog_type = 'CHSI_408'
                ORDER BY year DESC, imported_at DESC, id DESC
                LIMIT 1
                """, (rs, rowNum) -> new Catalog408ImportStatus(
                rs.getInt("year"),
                rs.getInt("is_complete") == 1,
                rs.getInt("input_records"),
                rs.getInt("school_count"),
                rs.getString("retrieved_at"),
                rs.getString("imported_at"),
                rs.getString("batch_sha256")
        ));
        return result.isEmpty() ? null : result.get(0);
    }

    public UpsertResult upsertSchool(Catalog408ImportRequest.School school, String region, String level) {
        List<Long> ids = jdbcTemplate.query("SELECT id FROM school WHERE name = ?",
                (rs, rowNum) -> rs.getLong("id"), school.name());
        if (!ids.isEmpty()) {
            jdbcTemplate.update("""
                    UPDATE school
                    SET province = COALESCE(NULLIF(province, ''), ?),
                        region = COALESCE(NULLIF(region, ''), ?),
                        school_level = COALESCE(NULLIF(school_level, ''), ?),
                        is_985 = CASE WHEN is_985 = 1 OR ? = 1 THEN 1 ELSE 0 END,
                        is_211 = CASE WHEN is_211 = 1 OR ? = 1 THEN 1 ELSE 0 END,
                        is_double_first_class = CASE WHEN is_double_first_class = 1 OR ? = 1 THEN 1 ELSE 0 END,
                        is_self_determined_score = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, school.province(), region, level, school.is985() ? 1 : 0,
                    school.is211() ? 1 : 0, school.isDoubleFirstClass() ? 1 : 0,
                    isSelfDeterminedScore(school.name()), ids.get(0));
            return new UpsertResult(ids.get(0), false);
        }
        long id = insertAndReturnId("""
                INSERT INTO school (name, province, region, school_level, is_985, is_211,
                  is_double_first_class, is_self_determined_score, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, school.name(), school.province(), region, level, school.is985() ? 1 : 0,
                school.is211() ? 1 : 0, school.isDoubleFirstClass() ? 1 : 0, isSelfDeterminedScore(school.name()),
                "基础信息来自中国研究生招生信息网当年硕士专业目录。");
        return new UpsertResult(id, true);
    }

    public UpsertResult upsertCollege(long schoolId, Catalog408ImportRequest.College college) {
        List<Long> ids = jdbcTemplate.query("SELECT id FROM college WHERE school_id = ? AND name = ?",
                (rs, rowNum) -> rs.getLong("id"), schoolId, college.name());
        if (!ids.isEmpty()) return new UpsertResult(ids.get(0), false);
        long id = insertAndReturnId("""
                INSERT INTO college (school_id, name, remark)
                VALUES (?, ?, ?)
                """, schoolId, college.name(), "院系名称来自当年研招网专业目录，代码 " + college.code() + "。");
        return new UpsertResult(id, true);
    }

    public UpsertResult upsertMajor(long schoolId, long collegeId, Catalog408ImportRequest.Major major,
                                    String directions, String remark) {
        List<ExistingMajor> existing = jdbcTemplate.query("""
                SELECT id, research_direction, remark FROM major
                WHERE school_id = ? AND college_id = ? AND major_code = ?
                  AND COALESCE(degree_type, '') = COALESCE(?, '')
                  AND COALESCE(study_mode, '') = COALESCE(?, '')
                ORDER BY id LIMIT 1
                """, (rs, rowNum) -> new ExistingMajor(
                rs.getLong("id"), rs.getString("research_direction"), rs.getString("remark")
        ), schoolId, collegeId, major.code(),
                major.degreeType(), major.studyMode());
        if (!existing.isEmpty()) {
            ExistingMajor current = existing.get(0);
            jdbcTemplate.update("""
                    UPDATE major SET name = ?, research_direction = ?, remark = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, major.name(), mergeDistinct(current.researchDirection(), directions, "、"),
                    mergeDistinct(current.remark(), remark, "；"), current.id());
            return new UpsertResult(current.id(), false);
        }
        long id = insertAndReturnId("""
                INSERT INTO major (school_id, college_id, name, major_code, degree_type,
                  research_direction, study_mode, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, schoolId, collegeId, major.name(), major.code(), major.degreeType(), directions,
                major.studyMode(), remark);
        return new UpsertResult(id, true);
    }

    public UpsertResult upsertSource(long schoolId, long collegeId, int year,
                                     Catalog408ImportRequest.Source source, String title) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id FROM document_source
                WHERE school_id = ? AND year = ? AND title = ?
                ORDER BY id LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), schoolId, year, title);
        if (!ids.isEmpty()) {
            jdbcTemplate.update("""
                    UPDATE document_source
                    SET source_type = ?, source_url = ?, college_id = ?, is_official = 1,
                        audit_status = 'PUBLISHED', remark = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, source.type(), source.url(), collegeId, "证据 SHA-256: " + source.sha256(), ids.get(0));
            return new UpsertResult(ids.get(0), false);
        }
        long id = insertAndReturnId("""
                INSERT INTO document_source (title, source_type, source_url, school_id, college_id,
                  year, is_official, audit_status, remark)
                VALUES (?, ?, ?, ?, ?, ?, 1, 'PUBLISHED', ?)
                """, title, source.type(), source.url(), schoolId, collegeId, year,
                "证据 SHA-256: " + source.sha256());
        return new UpsertResult(id, true);
    }

    public UpsertResult upsertDocument(long schoolId, long collegeId, long majorId, int year,
                                       String title, String sourceUrl, String rawText, String sourceHash) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id FROM source_document
                WHERE school_id = ? AND year = ? AND title = ?
                ORDER BY id LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), schoolId, year, title);
        if (!ids.isEmpty()) {
            long id = ids.get(0);
            jdbcTemplate.update("""
                    UPDATE source_document
                    SET source_url = ?, college_id = ?, major_id = ?, audit_status = 'PUBLISHED',
                        source_reliability = 'OFFICIAL', raw_text = ?, remark = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, sourceUrl, collegeId, majorId, rawText, "证据 SHA-256: " + sourceHash, id);
            replaceChunk(id, schoolId, collegeId, majorId, year, rawText);
            return new UpsertResult(id, false);
        }
        long id = insertAndReturnId("""
                INSERT INTO source_document (title, document_type, source_url, school_id, college_id,
                  major_id, year, audit_status, source_reliability, raw_text, remark)
                VALUES (?, '招生专业目录', ?, ?, ?, ?, ?, 'PUBLISHED', 'OFFICIAL', ?, ?)
                """, title, sourceUrl, schoolId, collegeId, majorId, year, rawText,
                "证据 SHA-256: " + sourceHash);
        replaceChunk(id, schoolId, collegeId, majorId, year, rawText);
        return new UpsertResult(id, true);
    }

    public UpsertResult upsertExamSubject(long schoolId, long collegeId, long majorId, int year,
                                          String politics, String foreignLanguage, String math,
                                          String professional, long sourceId) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id FROM exam_subject
                WHERE major_id = ? AND year = ? AND politics = ? AND foreign_language = ?
                  AND math_subject = ? AND professional_subject = ?
                ORDER BY id LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), majorId, year, politics, foreignLanguage, math, professional);
        if (!ids.isEmpty()) {
            jdbcTemplate.update("""
                    UPDATE exam_subject SET is_408 = 1, source_id = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, sourceId, ids.get(0));
            return new UpsertResult(ids.get(0), false);
        }
        long id = insertAndReturnId("""
                INSERT INTO exam_subject (school_id, college_id, major_id, year, politics,
                  foreign_language, math_subject, professional_subject, is_408, source_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                """, schoolId, collegeId, majorId, year, politics, foreignLanguage, math, professional, sourceId);
        return new UpsertResult(id, true);
    }

    public UpsertResult upsertCatalogAdmissionPlan(long schoolId, long collegeId, long majorId, int year,
                                                   int unifiedQuota, long sourceId, String quotaText) {
        List<ExistingPlan> existing = jdbcTemplate.query("""
                SELECT id, remark FROM admission_plan
                WHERE major_id = ? AND year = ?
                ORDER BY id
                """, (rs, rowNum) -> new ExistingPlan(rs.getLong("id"), rs.getString("remark")), majorId, year);
        ExistingPlan generated = existing.stream()
                .filter(plan -> plan.remark() != null && (plan.remark().startsWith(CATALOG_PLAN_MARKER)
                        || plan.remark().startsWith(LEGACY_CATALOG_PLAN_MARKER)))
                .findFirst().orElse(null);
        String remark = CATALOG_PLAN_MARKER + "原文：" + quotaText
                + "。结构化为统考计划；总计划和推免计划未推断。";
        if (generated != null) {
            jdbcTemplate.update("""
                    UPDATE admission_plan
                    SET school_id = ?, college_id = ?, total_quota = NULL, recommended_quota = NULL,
                        unified_quota = ?, has_adjustment = 0, source_id = ?, remark = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, schoolId, collegeId, unifiedQuota, sourceId, remark, generated.id());
            return new UpsertResult(generated.id(), false);
        }
        if (!existing.isEmpty()) {
            return new UpsertResult(existing.get(0).id(), false);
        }
        long id = insertAndReturnId("""
                INSERT INTO admission_plan (school_id, college_id, major_id, year, total_quota,
                  recommended_quota, unified_quota, has_adjustment, source_id, remark)
                VALUES (?, ?, ?, ?, NULL, NULL, ?, 0, ?, ?)
                """, schoolId, collegeId, majorId, year, unifiedQuota, sourceId, remark);
        return new UpsertResult(id, true);
    }

    public UpsertResult upsertCatalogRetestRule(long schoolId, long collegeId, long majorId, int year,
                                                String method, String ruleText, long sourceId) {
        List<ExistingRetestRule> existing = jdbcTemplate.query("""
                SELECT id, remark FROM retest_rule
                WHERE major_id = ? AND year = ?
                ORDER BY id
                """, (rs, rowNum) -> new ExistingRetestRule(rs.getLong("id"), rs.getString("remark")), majorId, year);
        ExistingRetestRule generated = existing.stream()
                .filter(rule -> rule.remark() != null && rule.remark().startsWith(CATALOG_RETEST_MARKER))
                .findFirst().orElse(null);
        String remark = CATALOG_RETEST_MARKER
                + "仅结构化目录中明确披露的复试内容；时间、差额比例和成绩权重未推断，需以学院复试方案核验。";
        if (generated != null) {
            jdbcTemplate.update("""
                    UPDATE retest_rule
                    SET school_id = ?, college_id = ?, retest_time = NULL, retest_method = ?,
                        retest_ratio = NULL, initial_score_weight = NULL, retest_score_weight = NULL,
                        qualification_line = ?, materials = NULL, source_id = ?, remark = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, schoolId, collegeId, method, ruleText, sourceId, remark, generated.id());
            return new UpsertResult(generated.id(), false);
        }
        if (!existing.isEmpty()) {
            return new UpsertResult(existing.get(0).id(), false);
        }
        long id = insertAndReturnId("""
                INSERT INTO retest_rule (school_id, college_id, major_id, year, retest_time,
                  retest_method, retest_ratio, initial_score_weight, retest_score_weight,
                  qualification_line, materials, source_id, remark)
                VALUES (?, ?, ?, ?, NULL, ?, NULL, NULL, NULL, ?, NULL, ?, ?)
                """, schoolId, collegeId, majorId, year, method, ruleText, sourceId, remark);
        return new UpsertResult(id, true);
    }

    private void replaceChunk(long documentId, long schoolId, long collegeId, long majorId,
                              int year, String content) {
        jdbcTemplate.update("DELETE FROM document_chunk WHERE document_id = ?", documentId);
        jdbcTemplate.update("""
                INSERT INTO document_chunk (document_id, school_id, college_id, major_id, year,
                  document_type, chunk_index, content, audit_status)
                VALUES (?, ?, ?, ?, ?, '招生专业目录', 0, ?, 'PUBLISHED')
                """, documentId, schoolId, collegeId, majorId, year, content);
    }

    private long insertAndReturnId(String sql, Object... params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < params.length; index++) statement.setObject(index + 1, params[index]);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id
                ? id
                : keyHolder.getKey();
        if (key == null) throw new IllegalStateException("未取得新增记录编号");
        return key.longValue();
    }

    public record UpsertResult(long id, boolean created) {
    }

    private String mergeDistinct(String existing, String incoming, String delimiter) {
        Set<String> values = new LinkedHashSet<>();
        addDelimited(values, existing, delimiter);
        addDelimited(values, incoming, delimiter);
        return String.join(delimiter, values);
    }

    private void addDelimited(Set<String> values, String text, String delimiter) {
        if (text == null || text.isBlank()) return;
        for (String value : text.split(Pattern.quote(delimiter))) {
            if (!value.isBlank()) values.add(value.trim());
        }
    }

    private record ExistingMajor(long id, String researchDirection, String remark) {
    }

    private record ExistingPlan(long id, String remark) {
    }

    private record ExistingRetestRule(long id, String remark) {
    }

    private int isSelfDeterminedScore(String schoolName) {
        return SELF_DETERMINED_SCORE_SCHOOLS.contains(schoolName) ? 1 : 0;
    }
}
