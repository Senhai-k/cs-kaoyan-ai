package com.kaoyan.assistant.quality;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DataCollectionTaskHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public DataCollectionTaskHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DataCollectionTaskHistory> findAll() {
        return jdbcTemplate.query("""
                SELECT id, school_id, action, from_status, to_status, operator, detail, created_at
                FROM data_collection_task_history
                ORDER BY created_at DESC, id DESC
                """, (rs, rowNum) -> new DataCollectionTaskHistory(
                rs.getLong("id"), rs.getLong("school_id"), rs.getString("action"),
                rs.getString("from_status"), rs.getString("to_status"), rs.getString("operator"),
                rs.getString("detail"), rs.getString("created_at")
        ));
    }

    public void save(Long schoolId, String action, String fromStatus, String toStatus,
                     String operator, String detail) {
        jdbcTemplate.update("""
                INSERT INTO data_collection_task_history (
                  school_id, action, from_status, to_status, operator, detail
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, schoolId, action, fromStatus, toStatus, operator, detail);
    }
}
