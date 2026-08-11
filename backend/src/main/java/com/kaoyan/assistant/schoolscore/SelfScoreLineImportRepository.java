package com.kaoyan.assistant.schoolscore;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class SelfScoreLineImportRepository {

    private static final String CATALOG_TYPE = "CHSI_SELF_SCORE_LINE";
    private final JdbcTemplate jdbcTemplate;

    public SelfScoreLineImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long findSchoolId(String schoolName) {
        List<Long> ids = jdbcTemplate.query("SELECT id FROM school WHERE name = ? ORDER BY id LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"), schoolName);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public long createSchool(SelfScoreLineImportRequest.ReviewedLine line) {
        return insertAndReturnId("""
                INSERT INTO school (name, province, city, region, school_level, is_985, is_211,
                  is_double_first_class, is_self_determined_score, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                """, line.schoolName(), line.province(), line.city(), regionFor(line.province()), line.schoolLevel(),
                Boolean.TRUE.equals(line.is985()) ? 1 : 0, Boolean.TRUE.equals(line.is211()) ? 1 : 0,
                Boolean.TRUE.equals(line.isDoubleFirstClass()) ? 1 : 0,
                "基础信息由2026年自主划线院校官方复试线批次补建；专业与考试科目仍须以当年目录核验。");
    }

    public UpsertResult upsertSource(long schoolId, int year, SelfScoreLineImportRequest.ReviewedLine line) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id FROM document_source
                WHERE school_id = ? AND year = ? AND title = ?
                ORDER BY id LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), schoolId, year, line.title());
        String remark = evidenceRemark(line);
        if (!ids.isEmpty()) {
            jdbcTemplate.update("""
                    UPDATE document_source
                    SET source_type = '学校基本线', source_url = ?, publish_date = ?, is_official = 1,
                        audit_status = 'PUBLISHED', remark = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, line.articleUrl(), line.publishedDate(), remark, ids.get(0));
            return new UpsertResult(ids.get(0), false);
        }
        long id = insertAndReturnId("""
                INSERT INTO document_source (title, source_type, source_url, publish_date, school_id,
                  year, is_official, audit_status, remark)
                VALUES (?, '学校基本线', ?, ?, ?, ?, 1, 'PUBLISHED', ?)
                """, line.title(), line.articleUrl(), line.publishedDate(), schoolId, year, remark);
        return new UpsertResult(id, true);
    }

    public UpsertResult upsertScoreLine(long schoolId, long sourceId, int year,
                                        SelfScoreLineImportRequest.ReviewedLine line) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id FROM school_score_line
                WHERE school_id = ? AND year = ? AND category_code = ? AND degree_type = ?
                ORDER BY id LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), schoolId, year, line.categoryCode(), line.degreeType());
        Object[] values = scoreLineValues(schoolId, sourceId, year, line);
        if (!ids.isEmpty()) {
            jdbcTemplate.update("""
                    UPDATE school_score_line SET category_name = ?, total_score = ?, politics_score = ?,
                      foreign_language_score = ?, subject_one_score = ?, subject_two_score = ?, score_100 = ?,
                      score_over_100 = ?, availability_status = ?, source_id = ?, source_title = ?, article_url = ?,
                      article_sha256 = ?, image_url = ?, image_sha256 = ?, published_date = ?, scope_note = ?,
                      remark = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, line.categoryName(), line.totalScore(), line.politicsScore(), line.foreignLanguageScore(),
                    line.subjectOneScore(), line.subjectTwoScore(), line.score100(), line.scoreOver100(),
                    line.availabilityStatus(), sourceId, line.title(), line.articleUrl(), line.articleSha256(),
                    line.imageUrl(), line.imageSha256(), line.publishedDate(), line.scopeNote(), line.remark(), ids.get(0));
            return new UpsertResult(ids.get(0), false);
        }
        long id = insertAndReturnId("""
                INSERT INTO school_score_line (school_id, year, category_code, category_name, degree_type,
                  total_score, politics_score, foreign_language_score, subject_one_score, subject_two_score,
                  score_100, score_over_100, availability_status, source_id, source_title, article_url,
                  article_sha256, image_url, image_sha256, published_date, scope_note, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, values);
        return new UpsertResult(id, true);
    }

    public UpsertResult upsertDocument(long schoolId, int year, SelfScoreLineImportRequest.ReviewedLine line,
                                       String content) {
        String title = line.title() + " - 计算机类学校基本线核验";
        List<Long> ids = jdbcTemplate.query("""
                SELECT id FROM source_document
                WHERE school_id = ? AND year = ? AND title = ?
                ORDER BY id LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), schoolId, year, title);
        String remark = evidenceRemark(line);
        if (!ids.isEmpty()) {
            long id = ids.get(0);
            jdbcTemplate.update("""
                    UPDATE source_document
                    SET document_type = '学校基本线', source_url = ?, audit_status = 'PUBLISHED',
                        source_reliability = 'OFFICIAL', raw_text = ?, remark = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, line.articleUrl(), content, remark, id);
            replaceChunk(id, schoolId, year, content);
            return new UpsertResult(id, false);
        }
        long id = insertAndReturnId("""
                INSERT INTO source_document (title, document_type, source_url, school_id, year,
                  audit_status, source_reliability, raw_text, remark)
                VALUES (?, '学校基本线', ?, ?, ?, 'PUBLISHED', 'OFFICIAL', ?, ?)
                """, title, line.articleUrl(), schoolId, year, content, remark);
        replaceChunk(id, schoolId, year, content);
        return new UpsertResult(id, true);
    }

    public void saveBatch(SelfScoreLineImportRequest request) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM catalog_import_batch
                WHERE catalog_type = ? AND year = ? AND batch_sha256 = ?
                """, Integer.class, CATALOG_TYPE, request.year(), request.batchSha256());
        if (count != null && count > 0) return;
        jdbcTemplate.update("""
                INSERT INTO catalog_import_batch (catalog_type, year, retrieved_at, is_complete,
                  input_records, school_count, batch_sha256)
                VALUES (?, ?, ?, 1, ?, ?, ?)
                """, CATALOG_TYPE, request.year(), request.retrievedAt(), request.records().size(),
                request.stats().schools(), request.batchSha256());
    }

    public SelfScoreLineImportStatus findLatestBatch() {
        List<SelfScoreLineImportStatus> result = jdbcTemplate.query("""
                SELECT year, input_records, school_count, retrieved_at, imported_at, batch_sha256
                FROM catalog_import_batch
                WHERE catalog_type = ?
                ORDER BY year DESC, imported_at DESC, id DESC LIMIT 1
                """, (rs, rowNum) -> new SelfScoreLineImportStatus(
                rs.getInt("year"), rs.getInt("input_records"), rs.getInt("school_count"),
                rs.getString("retrieved_at"), rs.getString("imported_at"), rs.getString("batch_sha256")
        ), CATALOG_TYPE);
        return result.isEmpty() ? null : result.get(0);
    }

    private Object[] scoreLineValues(long schoolId, long sourceId, int year,
                                     SelfScoreLineImportRequest.ReviewedLine line) {
        return new Object[]{schoolId, year, line.categoryCode(), line.categoryName(), line.degreeType(),
                line.totalScore(), line.politicsScore(), line.foreignLanguageScore(), line.subjectOneScore(),
                line.subjectTwoScore(), line.score100(), line.scoreOver100(), line.availabilityStatus(), sourceId,
                line.title(), line.articleUrl(), line.articleSha256(), line.imageUrl(), line.imageSha256(),
                line.publishedDate(), line.scopeNote(), line.remark()};
    }

    private void replaceChunk(long documentId, long schoolId, int year, String content) {
        jdbcTemplate.update("DELETE FROM document_chunk WHERE document_id = ?", documentId);
        jdbcTemplate.update("""
                INSERT INTO document_chunk (document_id, school_id, year, document_type, chunk_index,
                  content, audit_status)
                VALUES (?, ?, ?, '学校基本线', 0, ?, 'PUBLISHED')
                """, documentId, schoolId, year, content);
    }

    private String evidenceRemark(SelfScoreLineImportRequest.ReviewedLine line) {
        String image = line.imageSha256() == null ? "无对应图片" : "图片 SHA-256: " + line.imageSha256();
        return "文章 SHA-256: " + line.articleSha256() + "；" + image + "。";
    }

    private String regionFor(String province) {
        if (province == null) return "其他";
        if (List.of("北京", "天津", "河北", "山西", "内蒙古").contains(province)) return "华北";
        if (List.of("上海", "江苏", "浙江", "安徽", "福建", "江西", "山东").contains(province)) return "华东";
        if (List.of("河南", "湖北", "湖南").contains(province)) return "华中";
        if (List.of("广东", "广西", "海南").contains(province)) return "华南";
        if (List.of("辽宁", "吉林", "黑龙江").contains(province)) return "东北";
        if (List.of("重庆", "四川", "贵州", "云南", "西藏").contains(province)) return "西南";
        if (List.of("陕西", "甘肃", "青海", "宁夏", "新疆").contains(province)) return "西北";
        return "其他";
    }

    private long insertAndReturnId(String sql, Object... params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < params.length; index++) statement.setObject(index + 1, params[index]);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id
                ? id : keyHolder.getKey();
        if (key == null) throw new IllegalStateException("未取得新增记录编号");
        return key.longValue();
    }

    public record UpsertResult(long id, boolean created) {
    }
}
