package com.kaoyan.assistant.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class DocumentChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void deleteByDocumentId(Long documentId) {
        jdbcTemplate.update("DELETE FROM document_chunk WHERE document_id = ?", documentId);
    }

    public void create(DocumentChunkDto chunk) {
        jdbcTemplate.update("""
                INSERT INTO document_chunk (
                  document_id, school_id, college_id, major_id, year, document_type,
                  chunk_index, content, page_number, audit_status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                chunk.documentId(),
                chunk.schoolId(),
                chunk.collegeId(),
                chunk.majorId(),
                chunk.year(),
                chunk.documentType(),
                chunk.chunkIndex(),
                chunk.content(),
                chunk.pageNumber(),
                chunk.auditStatus());
    }

    public List<DocumentChunkDto> search(String keyword, Long schoolId, Integer year, String documentType, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, document_id, school_id, college_id, major_id, year, document_type,
                  chunk_index, content, page_number, audit_status, updated_at
                FROM document_chunk
                WHERE audit_status = 'PUBLISHED'
                """);
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND content LIKE ?");
            params.add("%" + keyword.trim() + "%");
        }
        if (schoolId != null) {
            sql.append(" AND school_id = ?");
            params.add(schoolId);
        }
        if (year != null) {
            sql.append(" AND year = ?");
            params.add(year);
        }
        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND document_type = ?");
            params.add(documentType.trim());
        }
        sql.append(" ORDER BY year DESC, document_id DESC, chunk_index ASC LIMIT ?");
        params.add(Math.max(1, Math.min(limit, 50)));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapChunk(rs), params.toArray());
    }

    public List<DocumentChunkDto> findByDocumentId(Long documentId) {
        return jdbcTemplate.query("""
                SELECT id, document_id, school_id, college_id, major_id, year, document_type,
                  chunk_index, content, page_number, audit_status, updated_at
                FROM document_chunk
                WHERE document_id = ?
                ORDER BY chunk_index ASC
                """, (rs, rowNum) -> mapChunk(rs), documentId);
    }

    private DocumentChunkDto mapChunk(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DocumentChunkDto(
                rs.getLong("id"),
                rs.getLong("document_id"),
                (Long) rs.getObject("school_id"),
                (Long) rs.getObject("college_id"),
                (Long) rs.getObject("major_id"),
                (Integer) rs.getObject("year"),
                rs.getString("document_type"),
                (Integer) rs.getObject("chunk_index"),
                rs.getString("content"),
                (Integer) rs.getObject("page_number"),
                rs.getString("audit_status"),
                rs.getString("updated_at")
        );
    }
}
