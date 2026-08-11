package com.kaoyan.assistant.rag;

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
public class SourceDocumentRepository {

    private static final String AUDIT_STATUS_ALL = "ALL";
    private static final String AUDIT_STATUS_PUBLISHED = "PUBLISHED";

    private final JdbcTemplate jdbcTemplate;

    public SourceDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SourceDocumentDto> findAll(Long schoolId, String auditStatus) {
        String normalizedAuditStatus = normalizeAuditStatusFilter(auditStatus);
        StringBuilder sql = new StringBuilder("""
                SELECT id, title, document_type, source_url, school_id, college_id, major_id,
                  year, audit_status, source_reliability, NULL AS raw_text, remark, updated_at
                FROM source_document
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
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapDocument(rs), params.toArray());
    }

    public SourceDocumentDto findById(Long id) {
        List<SourceDocumentDto> result = jdbcTemplate.query("""
                SELECT id, title, document_type, source_url, school_id, college_id, major_id,
                  year, audit_status, source_reliability, raw_text, remark, updated_at
                FROM source_document
                WHERE id = ?
                """, (rs, rowNum) -> mapDocument(rs), id);
        return result.isEmpty() ? null : result.get(0);
    }

    public SourceDocumentDto findByIdForUpdate(Long id) {
        List<SourceDocumentDto> result = jdbcTemplate.query("""
                SELECT id, title, document_type, source_url, school_id, college_id, major_id,
                  year, audit_status, source_reliability, raw_text, remark, updated_at
                FROM source_document
                WHERE id = ?
                FOR UPDATE
                """, (rs, rowNum) -> mapDocument(rs), id);
        return result.isEmpty() ? null : result.get(0);
    }

    public SourceDocumentDto findBySourceUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }
        List<SourceDocumentDto> result = jdbcTemplate.query("""
                SELECT id, title, document_type, source_url, school_id, college_id, major_id,
                  year, audit_status, source_reliability, raw_text, remark, updated_at
                FROM source_document
                WHERE source_url = ?
                ORDER BY id ASC
                LIMIT 1
                """, (rs, rowNum) -> mapDocument(rs), sourceUrl.trim());
        return result.isEmpty() ? null : result.get(0);
    }

    public Long create(SourceDocumentRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO source_document (
                      title, document_type, source_url, school_id, college_id, major_id,
                      year, audit_status, source_reliability, raw_text, remark
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.title());
            statement.setString(2, request.documentType());
            statement.setString(3, request.sourceUrl());
            statement.setObject(4, request.schoolId());
            statement.setObject(5, request.collegeId());
            statement.setObject(6, request.majorId());
            statement.setObject(7, request.year());
            statement.setString(8, normalizeAuditStatus(request.auditStatus()));
            statement.setString(9, normalizeSourceReliability(request.sourceReliability()));
            statement.setString(10, request.rawText());
            statement.setString(11, request.remark());
            return statement;
        }, keyHolder);
        Number id = extractGeneratedId(keyHolder);
        if (id == null) {
            throw new IllegalStateException("failed to create source document");
        }
        return id.longValue();
    }

    public void update(Long id, SourceDocumentRequest request) {
        jdbcTemplate.update("""
                UPDATE source_document
                SET title = ?, document_type = ?, source_url = ?, school_id = ?, college_id = ?,
                    major_id = ?, year = ?, audit_status = ?, source_reliability = ?, raw_text = ?, remark = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                request.title(),
                request.documentType(),
                request.sourceUrl(),
                request.schoolId(),
                request.collegeId(),
                request.majorId(),
                request.year(),
                normalizeAuditStatus(request.auditStatus()),
                normalizeSourceReliability(request.sourceReliability()),
                request.rawText(),
                request.remark(),
                id);
    }

    public int duplicateCount(SourceDocumentRequest request) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM source_document
                WHERE (
                    (source_url IS NOT NULL AND source_url <> '' AND source_url = ?)
                    OR (title = ? AND IFNULL(school_id, -1) = IFNULL(?, -1) AND IFNULL(year, -1) = IFNULL(?, -1))
                )
                """, Integer.class,
                request.sourceUrl(),
                request.title(),
                request.schoolId(),
                request.year());
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM document_chunk WHERE document_id = ?", id);
        jdbcTemplate.update("DELETE FROM source_document WHERE id = ?", id);
    }

    private SourceDocumentDto mapDocument(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SourceDocumentDto(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("document_type"),
                rs.getString("source_url"),
                (Long) rs.getObject("school_id"),
                (Long) rs.getObject("college_id"),
                (Long) rs.getObject("major_id"),
                (Integer) rs.getObject("year"),
                rs.getString("audit_status"),
                rs.getString("source_reliability"),
                rs.getString("raw_text"),
                rs.getString("remark"),
                rs.getString("updated_at")
        );
    }

    private String normalizeAuditStatus(String auditStatus) {
        return auditStatus == null || auditStatus.isBlank() ? "DRAFT" : auditStatus;
    }

    private String normalizeSourceReliability(String sourceReliability) {
        return sourceReliability == null || sourceReliability.isBlank() ? "UNKNOWN" : sourceReliability;
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
