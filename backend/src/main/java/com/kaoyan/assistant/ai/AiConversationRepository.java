package com.kaoyan.assistant.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AiConversationRepository {

    private final JdbcTemplate jdbcTemplate;

    public AiConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(String question, String answer, Long relatedSchoolId, String sourceSummary) {
        jdbcTemplate.update("""
                INSERT INTO ai_conversation (question, answer, related_school_id, source_summary)
                VALUES (?, ?, ?, ?)
                """,
                normalizeText(question),
                normalizeText(answer),
                relatedSchoolId,
                normalizeText(sourceSummary));
    }

    public List<AiConversationDto> findRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query("""
                SELECT id, question, answer, related_school_id, related_major_id, source_summary, created_at
                FROM ai_conversation
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """, (rs, rowNum) -> mapConversation(rs), safeLimit);
    }

    private AiConversationDto mapConversation(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AiConversationDto(
                rs.getLong("id"),
                rs.getString("question"),
                rs.getString("answer"),
                (Long) rs.getObject("related_school_id"),
                (Long) rs.getObject("related_major_id"),
                rs.getString("source_summary"),
                rs.getString("created_at")
        );
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
