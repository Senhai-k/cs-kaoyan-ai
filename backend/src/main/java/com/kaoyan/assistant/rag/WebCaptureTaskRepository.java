package com.kaoyan.assistant.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
public class WebCaptureTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public WebCaptureTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public WebCaptureTaskRecord findCompleted(Long targetId, String sha256) {
        return jdbcTemplate.query("""
                SELECT id, target_id, requested_url, final_url, content_sha256, http_status,
                  content_type, response_size, extractor_version, status, title, raw_text,
                  extracted_length, reuse_count, error_message, operator,
                  created_at, updated_at, completed_at
                FROM web_capture_task
                WHERE target_id = ? AND content_sha256 = ? AND status = 'COMPLETED'
                """, (rs, rowNum) -> mapRecord(rs), targetId, sha256)
                .stream().findFirst().orElse(null);
    }

    public WebCaptureTaskRecord findLatestCompleted(Long targetId) {
        return jdbcTemplate.query("""
                SELECT id, target_id, requested_url, final_url, content_sha256, http_status,
                  content_type, response_size, extractor_version, status, title, raw_text,
                  extracted_length, reuse_count, error_message, operator,
                  created_at, updated_at, completed_at
                FROM web_capture_task
                WHERE target_id = ? AND status = 'COMPLETED'
                ORDER BY updated_at DESC, id DESC LIMIT 1
                """, (rs, rowNum) -> mapRecord(rs), targetId).stream().findFirst().orElse(null);
    }

    public WebCaptureTaskRecord saveSuccess(CaptureInput input, CapturedContent content) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO web_capture_task (
                      target_id, requested_url, final_url, content_sha256, http_status,
                      content_type, response_size, extractor_version, status, title, raw_text,
                      extracted_length, error_message, operator, completed_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'COMPLETED', ?, ?, ?, NULL, ?, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setLong(1, input.targetId());
            statement.setString(2, input.requestedUrl());
            statement.setString(3, content.finalUrl());
            statement.setString(4, content.sha256());
            statement.setInt(5, content.httpStatus());
            statement.setString(6, content.contentType());
            statement.setLong(7, content.responseSize());
            statement.setString(8, input.extractorVersion());
            statement.setString(9, content.title());
            statement.setString(10, content.rawText());
            statement.setInt(11, content.rawText().length());
            statement.setString(12, input.operator());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue());
    }

    public WebCaptureTaskRecord markReused(Long targetId, String sha256, String operator) {
        jdbcTemplate.update("""
                UPDATE web_capture_task
                SET reuse_count = reuse_count + 1, operator = ?, updated_at = CURRENT_TIMESTAMP
                WHERE target_id = ? AND content_sha256 = ? AND status = 'COMPLETED'
                """, operator, targetId, sha256);
        return findCompleted(targetId, sha256);
    }

    public void saveFailure(CaptureInput input, String finalUrl, Integer httpStatus,
                            String contentType, String errorMessage) {
        jdbcTemplate.update("""
                INSERT INTO web_capture_task (
                  target_id, requested_url, final_url, http_status, content_type,
                  extractor_version, status, error_message, operator
                ) VALUES (?, ?, ?, ?, ?, ?, 'FAILED', ?, ?)
                """, input.targetId(), input.requestedUrl(), finalUrl, httpStatus, contentType,
                input.extractorVersion(), errorMessage, input.operator());
    }

    public List<WebCaptureTaskDto> findRecent(int limit) {
        return jdbcTemplate.query("""
                SELECT id, target_id, requested_url, final_url, content_sha256, http_status,
                  content_type, response_size, extractor_version, status, title,
                  extracted_length, reuse_count, error_message, operator,
                  created_at, updated_at, completed_at
                FROM web_capture_task ORDER BY updated_at DESC, id DESC LIMIT ?
                """, (rs, rowNum) -> new WebCaptureTaskDto(
                rs.getLong("id"), rs.getLong("target_id"), rs.getString("requested_url"),
                rs.getString("final_url"), rs.getString("content_sha256"),
                (Integer) rs.getObject("http_status"), rs.getString("content_type"),
                rs.getLong("response_size"), rs.getString("extractor_version"),
                rs.getString("status"), rs.getString("title"), rs.getInt("extracted_length"),
                rs.getInt("reuse_count"), rs.getString("error_message"), rs.getString("operator"),
                rs.getString("created_at"), rs.getString("updated_at"), rs.getString("completed_at")
        ), Math.max(1, Math.min(limit, 100)));
    }

    private WebCaptureTaskRecord findById(long id) {
        return jdbcTemplate.query("""
                SELECT id, target_id, requested_url, final_url, content_sha256, http_status,
                  content_type, response_size, extractor_version, status, title, raw_text,
                  extracted_length, reuse_count, error_message, operator,
                  created_at, updated_at, completed_at
                FROM web_capture_task WHERE id = ?
                """, (rs, rowNum) -> mapRecord(rs), id).stream().findFirst().orElseThrow();
    }

    private WebCaptureTaskRecord mapRecord(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new WebCaptureTaskRecord(
                rs.getLong("id"), rs.getLong("target_id"), rs.getString("requested_url"),
                rs.getString("final_url"), rs.getString("content_sha256"),
                (Integer) rs.getObject("http_status"), rs.getString("content_type"),
                rs.getLong("response_size"), rs.getString("extractor_version"),
                rs.getString("status"), rs.getString("title"), rs.getString("raw_text"),
                rs.getInt("extracted_length"), rs.getInt("reuse_count"),
                rs.getString("error_message"), rs.getString("operator"),
                rs.getString("created_at"), rs.getString("updated_at"), rs.getString("completed_at")
        );
    }

    public record CaptureInput(Long targetId, String requestedUrl, String extractorVersion, String operator) {
    }

    public record CapturedContent(String finalUrl, String sha256, Integer httpStatus,
                                  String contentType, Long responseSize, String title, String rawText) {
    }

    public record WebCaptureTaskRecord(
            Long id, Long targetId, String requestedUrl, String finalUrl, String contentSha256,
            Integer httpStatus, String contentType, Long responseSize, String extractorVersion,
            String status, String title, String rawText, Integer extractedLength,
            Integer reuseCount, String errorMessage, String operator, String createdAt,
            String updatedAt, String completedAt
    ) {
    }
}
