package com.kaoyan.assistant.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
public class DocumentParseTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentParseTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DocumentParseTaskRecord findByHash(String sha256) {
        return jdbcTemplate.query("""
                SELECT id, file_sha256, original_filename, content_type, file_size, parser_type,
                       parser_version, status, document_type, title, raw_text, remark,
                       extracted_length, reuse_count, error_message, operator,
                       created_at, updated_at, completed_at
                FROM document_parse_task WHERE file_sha256 = ?
                """, (rs, rowNum) -> mapRecord(rs), sha256).stream().findFirst().orElse(null);
    }

    public DocumentParseTaskRecord saveSuccess(ParseTaskInput input, ParsedContent content) {
        DocumentParseTaskRecord existing = findByHash(input.sha256());
        if (existing == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO document_parse_task (
                          file_sha256, original_filename, content_type, file_size, parser_type,
                          parser_version, status, document_type, title, raw_text, remark,
                          extracted_length, error_message, operator, completed_at
                        ) VALUES (?, ?, ?, ?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?, NULL, ?, CURRENT_TIMESTAMP)
                        """, new String[]{"id"});
                bindInput(statement, input);
                statement.setString(7, content.documentType());
                statement.setString(8, content.title());
                statement.setString(9, content.rawText());
                statement.setString(10, content.remark());
                statement.setInt(11, content.rawText().length());
                statement.setString(12, input.operator());
                return statement;
            }, keyHolder);
            return findById(keyHolder.getKey().longValue());
        }
        jdbcTemplate.update("""
                UPDATE document_parse_task
                SET original_filename = ?, content_type = ?, file_size = ?, parser_type = ?,
                    parser_version = ?, status = 'COMPLETED', document_type = ?, title = ?,
                    raw_text = ?, remark = ?, extracted_length = ?, error_message = NULL,
                    operator = ?, completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE file_sha256 = ?
                """, input.filename(), input.contentType(), input.fileSize(), input.parserType(),
                input.parserVersion(), content.documentType(), content.title(), content.rawText(),
                content.remark(), content.rawText().length(), input.operator(), input.sha256());
        return findByHash(input.sha256());
    }

    public void saveFailure(ParseTaskInput input, String error) {
        if (findByHash(input.sha256()) == null) {
            jdbcTemplate.update("""
                    INSERT INTO document_parse_task (
                      file_sha256, original_filename, content_type, file_size, parser_type,
                      parser_version, status, extracted_length, error_message, operator
                    ) VALUES (?, ?, ?, ?, ?, ?, 'FAILED', 0, ?, ?)
                    """, input.sha256(), input.filename(), input.contentType(), input.fileSize(),
                    input.parserType(), input.parserVersion(), error, input.operator());
            return;
        }
        jdbcTemplate.update("""
                UPDATE document_parse_task
                SET status = 'FAILED', raw_text = NULL, extracted_length = 0,
                    error_message = ?, operator = ?, completed_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE file_sha256 = ?
                """, error, input.operator(), input.sha256());
    }

    public DocumentParseTaskRecord markReused(String sha256) {
        jdbcTemplate.update("""
                UPDATE document_parse_task
                SET reuse_count = reuse_count + 1, updated_at = CURRENT_TIMESTAMP
                WHERE file_sha256 = ? AND status = 'COMPLETED'
                """, sha256);
        return findByHash(sha256);
    }

    public List<DocumentParseTaskDto> findRecent(int limit) {
        return jdbcTemplate.query("""
                SELECT id, file_sha256, original_filename, content_type, file_size, parser_type,
                       parser_version, status, document_type, title, extracted_length, reuse_count,
                       error_message, operator, created_at, updated_at, completed_at
                FROM document_parse_task ORDER BY updated_at DESC, id DESC LIMIT ?
                """, (rs, rowNum) -> new DocumentParseTaskDto(
                rs.getLong("id"), rs.getString("file_sha256"), rs.getString("original_filename"),
                rs.getString("content_type"), rs.getLong("file_size"), rs.getString("parser_type"),
                rs.getString("parser_version"), rs.getString("status"), rs.getString("document_type"),
                rs.getString("title"), rs.getInt("extracted_length"), rs.getInt("reuse_count"),
                rs.getString("error_message"), rs.getString("operator"),
                rs.getString("created_at"), rs.getString("updated_at"), rs.getString("completed_at")
        ), Math.max(1, Math.min(limit, 100)));
    }

    private DocumentParseTaskRecord findById(long id) {
        return jdbcTemplate.query("""
                SELECT id, file_sha256, original_filename, content_type, file_size, parser_type,
                       parser_version, status, document_type, title, raw_text, remark,
                       extracted_length, reuse_count, error_message, operator,
                       created_at, updated_at, completed_at
                FROM document_parse_task WHERE id = ?
                """, (rs, rowNum) -> mapRecord(rs), id).stream().findFirst().orElseThrow();
    }

    private DocumentParseTaskRecord mapRecord(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DocumentParseTaskRecord(
                rs.getLong("id"), rs.getString("file_sha256"), rs.getString("original_filename"),
                rs.getString("content_type"), rs.getLong("file_size"), rs.getString("parser_type"),
                rs.getString("parser_version"), rs.getString("status"), rs.getString("document_type"),
                rs.getString("title"), rs.getString("raw_text"), rs.getString("remark"),
                rs.getInt("extracted_length"), rs.getInt("reuse_count"), rs.getString("error_message"),
                rs.getString("operator"), rs.getString("created_at"), rs.getString("updated_at"),
                rs.getString("completed_at")
        );
    }

    private void bindInput(PreparedStatement statement, ParseTaskInput input) throws java.sql.SQLException {
        statement.setString(1, input.sha256());
        statement.setString(2, input.filename());
        statement.setString(3, input.contentType());
        statement.setLong(4, input.fileSize());
        statement.setString(5, input.parserType());
        statement.setString(6, input.parserVersion());
    }

    public record ParseTaskInput(String sha256, String filename, String contentType, long fileSize,
                                 String parserType, String parserVersion, String operator) {
    }

    public record ParsedContent(String title, String documentType, String rawText, String remark) {
    }

    public record DocumentParseTaskRecord(
            Long id, String sha256, String filename, String contentType, Long fileSize,
            String parserType, String parserVersion, String status, String documentType,
            String title, String rawText, String remark, Integer extractedLength,
            Integer reuseCount, String errorMessage, String operator, String createdAt,
            String updatedAt, String completedAt
    ) {
    }
}
