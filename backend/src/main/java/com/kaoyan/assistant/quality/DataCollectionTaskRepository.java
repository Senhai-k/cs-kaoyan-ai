package com.kaoyan.assistant.quality;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class DataCollectionTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public DataCollectionTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TaskState> findAll() {
        return jdbcTemplate.query("""
                SELECT school_id, status, assignee, due_date, completion_criteria,
                  criteria_custom, created_at, updated_at, completed_at
                FROM data_collection_task
                ORDER BY created_at ASC, school_id ASC
                """, this::mapTask);
    }

    public TaskState findBySchoolId(Long schoolId) {
        List<TaskState> result = jdbcTemplate.query("""
                SELECT school_id, status, assignee, due_date, completion_criteria,
                  criteria_custom, created_at, updated_at, completed_at
                FROM data_collection_task
                WHERE school_id = ?
                """, this::mapTask, schoolId);
        return result.isEmpty() ? null : result.get(0);
    }

    public SyncChange ensureOpenTask(Long schoolId, LocalDate dueDate, String completionCriteria) {
        TaskState existing = findBySchoolId(schoolId);
        if (existing != null) {
            boolean reopen = "COMPLETED".equals(existing.status());
            boolean criteriaChanged = !existing.criteriaCustom() && !completionCriteria.equals(existing.completionCriteria());
            if (!reopen && !criteriaChanged) {
                return SyncChange.NONE;
            }
            jdbcTemplate.update("""
                    UPDATE data_collection_task
                    SET status = CASE WHEN status = 'COMPLETED' THEN 'OPEN' ELSE status END,
                        completion_criteria = CASE WHEN criteria_custom = 0 THEN ? ELSE completion_criteria END,
                        completed_at = CASE WHEN status = 'COMPLETED' THEN NULL ELSE completed_at END,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE school_id = ?
                    """, completionCriteria, schoolId);
            return reopen ? SyncChange.REOPENED : SyncChange.REFRESHED;
        }
        jdbcTemplate.update("""
                INSERT INTO data_collection_task (
                  school_id, status, due_date, completion_criteria, criteria_custom
                ) VALUES (?, 'OPEN', ?, ?, 0)
                """, schoolId, dueDate, completionCriteria);
        return SyncChange.CREATED;
    }

    public boolean markCompleted(Long schoolId) {
        return jdbcTemplate.update("""
                UPDATE data_collection_task
                SET status = 'COMPLETED', completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP),
                    updated_at = CURRENT_TIMESTAMP
                WHERE school_id = ? AND status <> 'COMPLETED'
                """, schoolId) > 0;
    }

    public void update(Long schoolId, String status, String assignee, LocalDate dueDate,
                       String completionCriteria, boolean criteriaCustom) {
        jdbcTemplate.update("""
                UPDATE data_collection_task
                SET status = ?, assignee = ?, due_date = ?, completion_criteria = ?,
                    criteria_custom = ?,
                    completed_at = CASE WHEN ? = 'COMPLETED' THEN COALESCE(completed_at, CURRENT_TIMESTAMP) ELSE NULL END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE school_id = ?
                """, status, assignee, dueDate, completionCriteria, criteriaCustom ? 1 : 0, status, schoolId);
    }

    private TaskState mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new TaskState(
                rs.getLong("school_id"),
                rs.getString("status"),
                rs.getString("assignee"),
                rs.getString("due_date"),
                rs.getString("completion_criteria"),
                rs.getInt("criteria_custom") == 1,
                rs.getString("created_at"),
                rs.getString("updated_at"),
                rs.getString("completed_at")
        );
    }

    public record TaskState(
            Long schoolId,
            String status,
            String assignee,
            String dueDate,
            String completionCriteria,
            boolean criteriaCustom,
            String createdAt,
            String updatedAt,
            String completedAt
    ) {
    }

    public enum SyncChange {
        NONE, CREATED, REOPENED, REFRESHED
    }
}
