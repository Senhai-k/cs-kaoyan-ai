package com.kaoyan.assistant.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class WebCaptureChangeRepository {

    private final JdbcTemplate jdbcTemplate;

    public WebCaptureChangeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public WebCaptureChangeDto create(ChangeInput input) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO web_capture_change (
                      target_id, previous_task_id, current_task_id, previous_sha256, current_sha256,
                      previous_length, current_length, added_line_count, removed_line_count,
                      change_ratio, previous_excerpt, current_excerpt, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_REVIEW')
                    """, new String[]{"id"});
            statement.setLong(1, input.targetId());
            statement.setLong(2, input.previousTaskId());
            statement.setLong(3, input.currentTaskId());
            statement.setString(4, input.previousSha256());
            statement.setString(5, input.currentSha256());
            statement.setInt(6, input.previousLength());
            statement.setInt(7, input.currentLength());
            statement.setInt(8, input.addedLineCount());
            statement.setInt(9, input.removedLineCount());
            statement.setDouble(10, input.changeRatio());
            statement.setString(11, input.previousExcerpt());
            statement.setString(12, input.currentExcerpt());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue());
    }

    public List<WebCaptureChangeDto> findRecent(String status, int limit) {
        String normalized = normalizeStatusFilter(status);
        StringBuilder sql = new StringBuilder("""
                SELECT id, target_id, previous_task_id, current_task_id, previous_sha256,
                  current_sha256, previous_length, current_length, added_line_count,
                  removed_line_count, change_ratio, previous_excerpt, current_excerpt,
                  status, review_note, reviewer, detected_at, reviewed_at
                FROM web_capture_change WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (normalized != null) {
            sql.append(" AND status = ?");
            params.add(normalized);
        }
        sql.append(" ORDER BY detected_at DESC, id DESC LIMIT ?");
        params.add(Math.max(1, Math.min(limit, 100)));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> map(rs), params.toArray());
    }

    public WebCaptureChangeSummaryDto summary() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS total_count,
                  COALESCE(SUM(CASE WHEN status = 'PENDING_REVIEW' THEN 1 ELSE 0 END), 0) AS pending_count,
                  COALESCE(SUM(CASE WHEN status = 'ACKNOWLEDGED' THEN 1 ELSE 0 END), 0) AS acknowledged_count,
                  COALESCE(SUM(CASE WHEN status = 'IGNORED' THEN 1 ELSE 0 END), 0) AS ignored_count,
                  COALESCE(AVG(change_ratio), 0) AS average_ratio,
                  COALESCE(MAX(change_ratio), 0) AS max_ratio,
                  MIN(CASE WHEN status = 'PENDING_REVIEW' THEN detected_at ELSE NULL END) AS oldest_pending_at
                FROM web_capture_change
                """, (rs, rowNum) -> {
            Timestamp oldest = rs.getTimestamp("oldest_pending_at");
            Instant oldestInstant = oldest == null ? null : oldest.toInstant();
            long ageSeconds = oldestInstant == null ? 0
                    : Math.max(0, Duration.between(oldestInstant, Instant.now()).getSeconds());
            return new WebCaptureChangeSummaryDto(
                    rs.getLong("total_count"), rs.getLong("pending_count"),
                    rs.getLong("acknowledged_count"), rs.getLong("ignored_count"),
                    rs.getDouble("average_ratio"), rs.getDouble("max_ratio"),
                    oldestInstant == null ? null : oldestInstant.toString(), ageSeconds
            );
        });
    }

    public WebCaptureChangeDto findById(Long id) {
        return jdbcTemplate.query("""
                SELECT id, target_id, previous_task_id, current_task_id, previous_sha256,
                  current_sha256, previous_length, current_length, added_line_count,
                  removed_line_count, change_ratio, previous_excerpt, current_excerpt,
                  status, review_note, reviewer, detected_at, reviewed_at
                FROM web_capture_change WHERE id = ?
                """, (rs, rowNum) -> map(rs), id).stream().findFirst().orElse(null);
    }

    public WebCaptureChangeDto review(Long id, String status, String note, String reviewer) {
        int updated = jdbcTemplate.update("""
                UPDATE web_capture_change
                SET status = ?, review_note = ?, reviewer = ?, reviewed_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PENDING_REVIEW'
                """, status, note.trim(), normalizeOperator(reviewer), id);
        if (updated != 1) {
            throw new IllegalArgumentException("网页变更不存在或已处理");
        }
        return findById(id);
    }

    private WebCaptureChangeDto map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new WebCaptureChangeDto(
                rs.getLong("id"), rs.getLong("target_id"), rs.getLong("previous_task_id"),
                rs.getLong("current_task_id"), rs.getString("previous_sha256"),
                rs.getString("current_sha256"), rs.getInt("previous_length"),
                rs.getInt("current_length"), rs.getInt("added_line_count"),
                rs.getInt("removed_line_count"), rs.getDouble("change_ratio"),
                rs.getString("previous_excerpt"), rs.getString("current_excerpt"),
                rs.getString("status"), rs.getString("review_note"), rs.getString("reviewer"),
                rs.getString("detected_at"), rs.getString("reviewed_at")
        );
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return "PENDING_REVIEW";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalized)) {
            return null;
        }
        if (!("PENDING_REVIEW".equals(normalized) || "ACKNOWLEDGED".equals(normalized)
                || "IGNORED".equals(normalized))) {
            throw new IllegalArgumentException("不支持的网页变更状态");
        }
        return normalized;
    }

    private String normalizeOperator(String operator) {
        return operator == null || operator.isBlank() ? "system" : operator.trim();
    }

    public record ChangeInput(
            Long targetId, Long previousTaskId, Long currentTaskId,
            String previousSha256, String currentSha256,
            Integer previousLength, Integer currentLength,
            Integer addedLineCount, Integer removedLineCount,
            Double changeRatio, String previousExcerpt, String currentExcerpt
    ) {
    }
}
