package com.kaoyan.assistant.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
public class DocumentPublicationBatchRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentPublicationBatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long create(int documentCount, String reason, String operator) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO document_publication_batch (
                      status, document_count, chunk_count, reason, operator
                    ) VALUES ('PUBLISHING', ?, 0, ?, ?)
                    """, new String[]{"id"});
            statement.setInt(1, documentCount);
            statement.setString(2, normalizeText(reason));
            statement.setString(3, normalizeOperator(operator));
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void addItem(Long batchId, Long documentId, int previousVersionNo, int publishedVersionNo,
                        String previousAuditStatus) {
        jdbcTemplate.update("""
                INSERT INTO document_publication_batch_item (
                  batch_id, document_id, previous_version_no, published_version_no, previous_audit_status
                ) VALUES (?, ?, ?, ?, ?)
                """, batchId, documentId, previousVersionNo, publishedVersionNo, previousAuditStatus);
    }

    public void complete(Long batchId, int chunkCount) {
        jdbcTemplate.update("""
                UPDATE document_publication_batch
                SET status = 'PUBLISHED', chunk_count = ?, completed_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PUBLISHING'
                """, chunkCount, batchId);
    }

    public void markItemRolledBack(Long itemId, int rollbackVersionNo) {
        jdbcTemplate.update("""
                UPDATE document_publication_batch_item SET rollback_version_no = ? WHERE id = ?
                """, rollbackVersionNo, itemId);
    }

    public void markRolledBack(Long batchId, String reason, String operator, int chunkCount) {
        jdbcTemplate.update("""
                UPDATE document_publication_batch
                SET status = 'ROLLED_BACK', rollback_chunk_count = ?, rollback_reason = ?, rollback_operator = ?,
                    rolled_back_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PUBLISHED'
                """, chunkCount, normalizeText(reason), normalizeOperator(operator), batchId);
    }

    public DocumentPublicationBatchDto findById(Long id) {
        return jdbcTemplate.query("""
                SELECT id, status, document_count, chunk_count, rollback_chunk_count, reason, operator,
                  rollback_reason, rollback_operator, created_at, completed_at, rolled_back_at
                FROM document_publication_batch WHERE id = ?
                """, (rs, rowNum) -> mapBatch(rs), id).stream().findFirst().orElse(null);
    }

    public DocumentPublicationBatchDto findByIdForUpdate(Long id) {
        return jdbcTemplate.query("""
                SELECT id, status, document_count, chunk_count, rollback_chunk_count, reason, operator,
                  rollback_reason, rollback_operator, created_at, completed_at, rolled_back_at
                FROM document_publication_batch WHERE id = ? FOR UPDATE
                """, (rs, rowNum) -> mapBatch(rs), id).stream().findFirst().orElse(null);
    }

    public List<DocumentPublicationBatchDto> findRecent(int limit) {
        return jdbcTemplate.query("""
                SELECT id, status, document_count, chunk_count, rollback_chunk_count, reason, operator,
                  rollback_reason, rollback_operator, created_at, completed_at, rolled_back_at
                FROM document_publication_batch ORDER BY created_at DESC, id DESC LIMIT ?
                """, (rs, rowNum) -> mapBatch(rs), Math.max(1, Math.min(limit, 100)));
    }

    public List<BatchItem> findItems(Long batchId) {
        return jdbcTemplate.query("""
                SELECT id, batch_id, document_id, previous_version_no, published_version_no,
                  rollback_version_no, previous_audit_status
                FROM document_publication_batch_item WHERE batch_id = ? ORDER BY id
                """, (rs, rowNum) -> new BatchItem(
                rs.getLong("id"), rs.getLong("batch_id"), rs.getLong("document_id"),
                rs.getInt("previous_version_no"), rs.getInt("published_version_no"),
                (Integer) rs.getObject("rollback_version_no"), rs.getString("previous_audit_status")
        ), batchId);
    }

    private DocumentPublicationBatchDto mapBatch(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DocumentPublicationBatchDto(
                rs.getLong("id"), rs.getString("status"), rs.getInt("document_count"),
                rs.getInt("chunk_count"), (Integer) rs.getObject("rollback_chunk_count"),
                rs.getString("reason"), rs.getString("operator"),
                rs.getString("rollback_reason"), rs.getString("rollback_operator"),
                rs.getString("created_at"), rs.getString("completed_at"), rs.getString("rolled_back_at")
        );
    }

    private String normalizeOperator(String operator) {
        return operator == null || operator.isBlank() ? "system" : operator.trim();
    }

    private String normalizeText(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    public record BatchItem(
            Long id, Long batchId, Long documentId, Integer previousVersionNo,
            Integer publishedVersionNo, Integer rollbackVersionNo, String previousAuditStatus
    ) {
    }
}
