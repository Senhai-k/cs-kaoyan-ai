package com.kaoyan.assistant.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DataChangeLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public DataChangeLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(String operator, String action, String resourcePath, int statusCode) {
        jdbcTemplate.update("""
                INSERT INTO data_change_log (operator, action, resource_path, status_code)
                VALUES (?, ?, ?, ?)
                """, operator, action, resourcePath, statusCode);
    }
}
