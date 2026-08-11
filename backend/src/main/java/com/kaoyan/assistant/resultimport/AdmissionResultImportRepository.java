package com.kaoyan.assistant.resultimport;

import com.kaoyan.assistant.common.JdbcValues;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class AdmissionResultImportRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdmissionResultImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdmissionResultImportBatchDto> findAll() {
        return jdbcTemplate.query("""
                SELECT id, school_id, year, source_id, source_sha256, batch_sha256, status,
                  input_records, group_count, mapped_group_count, remark, created_at, updated_at, published_at
                FROM admission_result_import_batch
                ORDER BY created_at DESC, id DESC
                """, (rs, rowNum) -> mapBatch(rs));
    }

    public AdmissionResultImportBatchDto findById(long id) {
        List<AdmissionResultImportBatchDto> rows = jdbcTemplate.query("""
                SELECT id, school_id, year, source_id, source_sha256, batch_sha256, status,
                  input_records, group_count, mapped_group_count, remark, created_at, updated_at, published_at
                FROM admission_result_import_batch WHERE id = ?
                """, (rs, rowNum) -> mapBatch(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public AdmissionResultImportBatchDto findByHash(String batchSha256) {
        List<AdmissionResultImportBatchDto> rows = jdbcTemplate.query("""
                SELECT id, school_id, year, source_id, source_sha256, batch_sha256, status,
                  input_records, group_count, mapped_group_count, remark, created_at, updated_at, published_at
                FROM admission_result_import_batch WHERE batch_sha256 = ?
                """, (rs, rowNum) -> mapBatch(rs), batchSha256);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long createBatch(AdmissionResultImportRequest request, AdmissionResultImportPreview preview) {
        return insertAndReturnId("""
                INSERT INTO admission_result_import_batch (school_id, year, source_id, source_sha256,
                  batch_sha256, status, input_records, group_count, mapped_group_count, remark)
                VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?)
                """, request.schoolId(), request.year(), request.sourceId(), request.sourceSha256(),
                request.batchSha256(), preview.inputRecords(), preview.groupCount(), preview.mappedGroupCount(),
                request.remark());
    }

    public void insertCandidate(long batchId, AdmissionResultImportRequest.CandidateRecord record) {
        jdbcTemplate.update("""
                INSERT INTO admission_result_candidate (batch_id, candidate_key, college_name, major_code,
                  major_name, degree_type, study_mode, candidate_type, initial_score, retest_score,
                  final_score, special_program)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batchId, record.candidateKey().toLowerCase(), record.collegeName().trim(),
                record.majorCode().trim(), normalizeNullable(record.majorName()), record.degreeType().trim(),
                record.studyMode().trim(), record.candidateType().trim(), record.initialScore(),
                record.retestScore(), record.finalScore(), normalizeNullable(record.specialProgram()));
    }

    public List<CandidateRow> findCandidates(long batchId) {
        return jdbcTemplate.query("""
                SELECT candidate_key, college_name, major_code, major_name, degree_type, study_mode,
                  candidate_type, initial_score, retest_score, final_score, special_program
                FROM admission_result_candidate
                WHERE batch_id = ? ORDER BY id
                """, (rs, rowNum) -> new CandidateRow(
                rs.getString("candidate_key"), rs.getString("college_name"), rs.getString("major_code"),
                rs.getString("major_name"), rs.getString("degree_type"), rs.getString("study_mode"),
                rs.getString("candidate_type"), (Integer) rs.getObject("initial_score"),
                JdbcValues.nullableDouble(rs, "retest_score"), JdbcValues.nullableDouble(rs, "final_score"),
                rs.getString("special_program")
        ), batchId);
    }

    public List<String> findCandidateKeys(long batchId) {
        return jdbcTemplate.queryForList("""
                SELECT candidate_key FROM admission_result_candidate
                WHERE batch_id = ? ORDER BY candidate_key
                """, String.class, batchId);
    }

    public List<MajorMatch> findMajorMatches(long schoolId, String collegeName, String majorCode,
                                             String degreeType, String studyMode) {
        return jdbcTemplate.query("""
                SELECT c.id AS college_id, m.id AS major_id
                FROM major m JOIN college c ON c.id = m.college_id
                WHERE m.school_id = ? AND c.name = ? AND m.major_code = ?
                  AND COALESCE(m.degree_type, '') = COALESCE(?, '')
                  AND COALESCE(m.study_mode, '') = COALESCE(?, '')
                ORDER BY m.id
                """, (rs, rowNum) -> new MajorMatch(rs.getLong("college_id"), rs.getLong("major_id")),
                schoolId, collegeName, majorCode, degreeType, studyMode);
    }

    public ExistingResult findExistingResult(long majorId, int year) {
        List<ExistingResult> rows = jdbcTemplate.query("""
                SELECT id, remark FROM admission_result
                WHERE major_id = ? AND year = ? ORDER BY id LIMIT 1
                """, (rs, rowNum) -> new ExistingResult(rs.getLong("id"), rs.getString("remark")), majorId, year);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long createAdmissionResult(long schoolId, long collegeId, long majorId, int year,
                                      AdmissionResultImportPreview.GroupPreview group, long sourceId,
                                      long batchId) {
        String remark = "拟录取名单批次｜" + batchId + "｜按匿名候选人键去重；初试分覆盖 "
                + group.scoreCoverageCount() + "/" + group.admittedCount()
                + "；口径：" + group.candidateType() + "、" + group.studyMode() + "。";
        return insertAndReturnId("""
                INSERT INTO admission_result (school_id, college_id, major_id, year, admitted_count,
                  lowest_score, average_score, highest_score, retest_ratio, source_id, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
                """, schoolId, collegeId, majorId, year, group.admittedCount(), group.lowestScore(),
                group.averageScore(), group.highestScore(), sourceId, remark);
    }

    public void markPublished(long batchId) {
        jdbcTemplate.update("""
                UPDATE admission_result_import_batch
                SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, batchId);
    }

    private AdmissionResultImportBatchDto mapBatch(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AdmissionResultImportBatchDto(
                rs.getLong("id"), rs.getLong("school_id"), rs.getInt("year"),
                rs.getLong("source_id"), rs.getString("source_sha256"), rs.getString("batch_sha256"),
                rs.getString("status"), rs.getInt("input_records"), rs.getInt("group_count"),
                rs.getInt("mapped_group_count"), rs.getString("remark"), rs.getString("created_at"),
                rs.getString("updated_at"), rs.getString("published_at")
        );
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

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CandidateRow(String candidateKey, String collegeName, String majorCode, String majorName,
                               String degreeType, String studyMode, String candidateType, Integer initialScore,
                               Double retestScore, Double finalScore, String specialProgram) {
    }

    public record MajorMatch(long collegeId, long majorId) {
    }

    public record ExistingResult(long id, String remark) {
    }
}
