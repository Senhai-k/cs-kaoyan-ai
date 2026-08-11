package com.kaoyan.assistant.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SourceDocumentVersionRepository {

    private final JdbcTemplate jdbcTemplate;

    public SourceDocumentVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SourceDocumentVersionDto snapshot(SourceDocumentDto document, String operation, String operator) {
        int nextVersion = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version_no), 0) + 1
                FROM source_document_version
                WHERE document_id = ?
                """, Integer.class, document.id());
        jdbcTemplate.update("""
                INSERT INTO source_document_version (
                  document_id, version_no, title, document_type, source_url, school_id, college_id,
                  major_id, year, audit_status, source_reliability, raw_text, remark, operation,
                  operator, source_updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                document.id(), nextVersion, document.title(), document.documentType(),
                document.sourceUrl(), document.schoolId(), document.collegeId(), document.majorId(),
                document.year(), document.auditStatus(), document.sourceReliability(),
                document.rawText(), document.remark(), operation, normalizeOperator(operator),
                document.updatedAt());
        return find(document.id(), nextVersion);
    }

    public List<SourceDocumentVersionDto> findAll(Long documentId) {
        return jdbcTemplate.query("""
                SELECT id, document_id, version_no, title, document_type, source_url, school_id,
                  college_id, major_id, year, audit_status, source_reliability, raw_text, remark,
                  operation, operator, source_updated_at, created_at
                FROM source_document_version
                WHERE document_id = ?
                ORDER BY version_no DESC
                """, (rs, rowNum) -> map(rs), documentId);
    }

    public SourceDocumentVersionDto find(Long documentId, Integer versionNo) {
        List<SourceDocumentVersionDto> versions = jdbcTemplate.query("""
                SELECT id, document_id, version_no, title, document_type, source_url, school_id,
                  college_id, major_id, year, audit_status, source_reliability, raw_text, remark,
                  operation, operator, source_updated_at, created_at
                FROM source_document_version
                WHERE document_id = ? AND version_no = ?
                """, (rs, rowNum) -> map(rs), documentId, versionNo);
        return versions.isEmpty() ? null : versions.get(0);
    }

    public boolean hasVersions(Long documentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM source_document_version WHERE document_id = ?",
                Integer.class, documentId
        );
        return count != null && count > 0;
    }

    public Integer latestVersionNo(Long documentId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version_no), 0)
                FROM source_document_version
                WHERE document_id = ?
                """, Integer.class, documentId);
    }

    public void deleteByDocumentId(Long documentId) {
        jdbcTemplate.update("DELETE FROM source_document_version WHERE document_id = ?", documentId);
    }

    private SourceDocumentVersionDto map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SourceDocumentVersionDto(
                rs.getLong("id"),
                rs.getLong("document_id"),
                rs.getInt("version_no"),
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
                rs.getString("operation"),
                rs.getString("operator"),
                rs.getString("source_updated_at"),
                rs.getString("created_at")
        );
    }

    private String normalizeOperator(String operator) {
        return operator == null || operator.isBlank() ? "system" : operator.trim();
    }
}
