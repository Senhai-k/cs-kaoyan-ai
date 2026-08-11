package com.kaoyan.assistant.source;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class DocumentSourceRepository {

    private static final String AUDIT_STATUS_ALL = "ALL";
    private static final String AUDIT_STATUS_PUBLISHED = "PUBLISHED";

    private final JdbcTemplate jdbcTemplate;

    public DocumentSourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DocumentSourceDto> findAll(Long schoolId, String auditStatus) {
        String normalizedAuditStatus = normalizeAuditStatusFilter(auditStatus);
        StringBuilder sql = new StringBuilder("""
                SELECT id, title, source_type, source_url, publish_date, school_id,
                  college_id, year, is_official, audit_status, updated_at, remark
                FROM document_source
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (schoolId != null) {
            sql.append(" AND school_id = ?");
            params.add(schoolId);
        }
        if (normalizedAuditStatus != null) {
            sql.append(" AND audit_status = ?");
            params.add(normalizedAuditStatus);
        }
        sql.append(" ORDER BY year DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DocumentSourceDto(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("source_type"),
                rs.getString("source_url"),
                rs.getString("publish_date"),
                (Long) rs.getObject("school_id"),
                (Long) rs.getObject("college_id"),
                (Integer) rs.getObject("year"),
                rs.getInt("is_official") == 1,
                rs.getString("audit_status"),
                rs.getString("updated_at"),
                rs.getString("remark")
        ), params.toArray());
    }

    public DocumentSourceDto findById(Long id) {
        List<DocumentSourceDto> result = jdbcTemplate.query("""
                SELECT id, title, source_type, source_url, publish_date, school_id,
                  college_id, year, is_official, audit_status, updated_at, remark
                FROM document_source
                WHERE id = ?
                """, (rs, rowNum) -> new DocumentSourceDto(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("source_type"),
                rs.getString("source_url"),
                rs.getString("publish_date"),
                (Long) rs.getObject("school_id"),
                (Long) rs.getObject("college_id"),
                (Integer) rs.getObject("year"),
                rs.getInt("is_official") == 1,
                rs.getString("audit_status"),
                rs.getString("updated_at"),
                rs.getString("remark")
        ), id);
        return result.isEmpty() ? null : result.get(0);
    }

    public Long create(DocumentSourceRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO document_source (
                      title, source_type, source_url, publish_date, school_id, college_id,
                      year, is_official, audit_status, remark
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.title());
            statement.setString(2, request.sourceType());
            statement.setString(3, request.sourceUrl());
            statement.setString(4, request.publishDate());
            statement.setObject(5, request.schoolId());
            statement.setObject(6, request.collegeId());
            statement.setObject(7, request.year());
            statement.setInt(8, request.official() ? 1 : 0);
            statement.setString(9, normalizeAuditStatus(request.auditStatus()));
            statement.setString(10, request.remark());
            return statement;
        }, keyHolder);
        Number id = extractGeneratedId(keyHolder);
        if (id == null) {
            throw new IllegalStateException("failed to create document source");
        }
        return id.longValue();
    }

    public void update(Long id, DocumentSourceRequest request) {
        jdbcTemplate.update("""
                UPDATE document_source
                SET title = ?, source_type = ?, source_url = ?, publish_date = ?, school_id = ?,
                    college_id = ?, year = ?, is_official = ?, audit_status = ?, remark = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                request.title(), request.sourceType(), request.sourceUrl(), request.publishDate(),
                request.schoolId(), request.collegeId(), request.year(),
                request.official() ? 1 : 0, normalizeAuditStatus(request.auditStatus()), request.remark(), id);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM document_source WHERE id = ?", id);
    }

    private String normalizeAuditStatus(String auditStatus) {
        if (auditStatus == null || auditStatus.isBlank()) {
            return "PUBLISHED";
        }
        return auditStatus;
    }

    private Number extractGeneratedId(KeyHolder keyHolder) {
        if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id) {
            return id;
        }
        return keyHolder.getKey();
    }

    private String normalizeAuditStatusFilter(String auditStatus) {
        if (auditStatus == null || auditStatus.isBlank()) {
            return AUDIT_STATUS_PUBLISHED;
        }
        return AUDIT_STATUS_ALL.equalsIgnoreCase(auditStatus.trim())
                ? null
                : auditStatus.trim().toUpperCase(Locale.ROOT);
    }
}
