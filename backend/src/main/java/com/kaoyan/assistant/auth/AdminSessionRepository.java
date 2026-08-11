package com.kaoyan.assistant.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class AdminSessionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final boolean forceH2Checkpoint;

    public AdminSessionRepository(JdbcTemplate jdbcTemplate,
                                  @Value("${app.admin.session.h2-checkpoint:false}") boolean forceH2Checkpoint) {
        this.jdbcTemplate = jdbcTemplate;
        this.forceH2Checkpoint = forceH2Checkpoint;
    }

    public void create(String tokenHash, String username, AdminRole role, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO admin_session (token_hash, username, role, expires_at)
                VALUES (?, ?, ?, ?)
                """, tokenHash, username, role.name(), Timestamp.from(expiresAt));
        flushH2FileStore();
    }

    public AdminPrincipal findActive(String tokenHash, Instant now) {
        return jdbcTemplate.query("""
                SELECT username, role, expires_at
                FROM admin_session
                WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > ?
                """, (rs, rowNum) -> new AdminPrincipal(
                rs.getString("username"),
                AdminRole.valueOf(rs.getString("role")),
                rs.getTimestamp("expires_at").toInstant().getEpochSecond()
        ), tokenHash, Timestamp.from(now)).stream().findFirst().orElse(null);
    }

    public void touch(String tokenHash, Instant now) {
        jdbcTemplate.update(
                "UPDATE admin_session SET last_used_at = ? WHERE token_hash = ?",
                Timestamp.from(now), tokenHash
        );
    }

    public void revoke(String tokenHash, Instant now) {
        jdbcTemplate.update("""
                UPDATE admin_session
                SET revoked_at = ?
                WHERE token_hash = ? AND revoked_at IS NULL
                """, Timestamp.from(now), tokenHash);
    }

    public void revokeAllForUser(String username, Instant now) {
        jdbcTemplate.update("""
                UPDATE admin_session
                SET revoked_at = ?
                WHERE username = ? AND revoked_at IS NULL
                """, Timestamp.from(now), username);
        flushH2FileStore();
    }

    public void deleteExpired(Instant now) {
        jdbcTemplate.update(
                "DELETE FROM admin_session WHERE expires_at <= ? OR revoked_at IS NOT NULL",
                Timestamp.from(now)
        );
    }

    private void flushH2FileStore() {
        if (!forceH2Checkpoint) {
            return;
        }
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            if ("H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                try (var statement = connection.createStatement()) {
                    statement.execute("CHECKPOINT SYNC");
                }
            }
            return null;
        });
    }
}
