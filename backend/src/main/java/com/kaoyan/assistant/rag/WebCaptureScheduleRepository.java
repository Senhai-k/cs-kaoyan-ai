package com.kaoyan.assistant.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class WebCaptureScheduleRepository {

    private final JdbcTemplate jdbcTemplate;

    public WebCaptureScheduleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WebCaptureScheduleDto> findAll() {
        return jdbcTemplate.query("""
                SELECT s.target_id, t.title AS target_title, t.source_url, s.enabled,
                  s.interval_hours, s.next_run_at, s.lease_until, s.last_started_at,
                  s.last_finished_at, s.last_status, s.last_error, s.consecutive_failures,
                  s.updated_by, s.updated_at
                FROM web_capture_schedule s
                JOIN data_collection_target t ON t.id = s.target_id
                ORDER BY s.enabled DESC, s.next_run_at ASC, s.target_id ASC
                """, (rs, rowNum) -> map(rs));
    }

    public WebCaptureScheduleDto findByTargetId(Long targetId) {
        return findAll().stream().filter(item -> item.targetId().equals(targetId))
                .findFirst().orElse(null);
    }

    @Transactional
    public WebCaptureScheduleDto configure(Long targetId, boolean enabled, int intervalHours, String operator) {
        int updated = jdbcTemplate.update("""
                UPDATE web_capture_schedule
                SET enabled = ?, interval_hours = ?,
                  next_run_at = CASE WHEN ? = 1 THEN CURRENT_TIMESTAMP ELSE next_run_at END,
                  lease_owner = NULL, lease_until = NULL, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE target_id = ?
                """, enabled ? 1 : 0, intervalHours, enabled ? 1 : 0, operator, targetId);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO web_capture_schedule (
                      target_id, enabled, interval_hours, next_run_at, updated_by
                    ) VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)
                    """, targetId, enabled ? 1 : 0, intervalHours, operator);
        }
        return findByTargetId(targetId);
    }

    @Transactional
    public ScheduleClaim claimNext(String owner, int leaseSeconds) {
        ScheduleClaim claim = jdbcTemplate.query("""
                SELECT target_id, interval_hours, consecutive_failures
                FROM web_capture_schedule
                WHERE enabled = 1 AND next_run_at <= CURRENT_TIMESTAMP
                  AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP)
                ORDER BY next_run_at ASC, target_id ASC
                LIMIT 1 FOR UPDATE
                """, (rs, rowNum) -> new ScheduleClaim(
                rs.getLong("target_id"), rs.getInt("interval_hours"),
                rs.getInt("consecutive_failures")
        )).stream().findFirst().orElse(null);
        if (claim == null) {
            return null;
        }
        Instant leaseUntil = Instant.now().plusSeconds(Math.max(30, leaseSeconds));
        jdbcTemplate.update("""
                UPDATE web_capture_schedule
                SET lease_owner = ?, lease_until = ?, last_started_at = CURRENT_TIMESTAMP,
                  last_status = 'RUNNING', last_error = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE target_id = ?
                """, owner, Timestamp.from(leaseUntil), claim.targetId());
        return claim;
    }

    public void completeSuccess(ScheduleClaim claim, String owner) {
        Instant nextRun = Instant.now().plusSeconds(claim.intervalHours() * 3600L);
        jdbcTemplate.update("""
                UPDATE web_capture_schedule
                SET next_run_at = ?, lease_owner = NULL, lease_until = NULL,
                  last_finished_at = CURRENT_TIMESTAMP, last_status = 'COMPLETED',
                  last_error = NULL, consecutive_failures = 0, updated_at = CURRENT_TIMESTAMP
                WHERE target_id = ? AND lease_owner = ?
                """, Timestamp.from(nextRun), claim.targetId(), owner);
    }

    public void completeFailure(ScheduleClaim claim, String owner, String error) {
        int failures = claim.consecutiveFailures() + 1;
        long backoffHours = Math.min(claim.intervalHours(), 1L << Math.min(failures - 1, 5));
        Instant nextRun = Instant.now().plusSeconds(Math.max(1, backoffHours) * 3600L);
        String safeError = error == null || error.isBlank() ? "scheduled capture failed" : error.trim();
        if (safeError.length() > 500) {
            safeError = safeError.substring(0, 500);
        }
        jdbcTemplate.update("""
                UPDATE web_capture_schedule
                SET next_run_at = ?, lease_owner = NULL, lease_until = NULL,
                  last_finished_at = CURRENT_TIMESTAMP, last_status = 'FAILED',
                  last_error = ?, consecutive_failures = ?, updated_at = CURRENT_TIMESTAMP
                WHERE target_id = ? AND lease_owner = ?
                """, Timestamp.from(nextRun), safeError, failures, claim.targetId(), owner);
    }

    private WebCaptureScheduleDto map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new WebCaptureScheduleDto(
                rs.getLong("target_id"), rs.getString("target_title"), rs.getString("source_url"),
                rs.getInt("enabled") == 1, rs.getInt("interval_hours"),
                rs.getString("next_run_at"), rs.getString("lease_until"),
                rs.getString("last_started_at"), rs.getString("last_finished_at"),
                rs.getString("last_status"), rs.getString("last_error"),
                rs.getInt("consecutive_failures"), rs.getString("updated_by"),
                rs.getString("updated_at")
        );
    }

    public record ScheduleClaim(Long targetId, int intervalHours, int consecutiveFailures) {
    }
}
