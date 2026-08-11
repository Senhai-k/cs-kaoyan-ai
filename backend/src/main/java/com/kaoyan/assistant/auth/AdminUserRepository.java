package com.kaoyan.assistant.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminUserRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminUserCredential findActiveByUsername(String username) {
        return jdbcTemplate.query("""
                SELECT username, password_hash, role
                FROM admin_user
                WHERE username = ? AND status = 1
                """, (rs, rowNum) -> new AdminUserCredential(
                rs.getString("username"),
                rs.getString("password_hash"),
                AdminRole.valueOf(rs.getString("role"))
        ), username).stream().findFirst().orElse(null);
    }

    public void ensureConfiguredUser(String username, String passwordHash, AdminRole role, boolean replacePassword) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_user WHERE username = ?", Integer.class, username
        );
        if (count != null && count > 0) {
            if (replacePassword) {
                jdbcTemplate.update("""
                        UPDATE admin_user
                        SET password_hash = ?, role = ?, status = 1, updated_at = CURRENT_TIMESTAMP
                        WHERE username = ?
                        """, passwordHash, role.name(), username);
            } else {
                jdbcTemplate.update("""
                        UPDATE admin_user
                        SET role = ?, status = 1, updated_at = CURRENT_TIMESTAMP
                        WHERE username = ?
                        """, role.name(), username);
            }
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO admin_user (username, password_hash, nickname, role, status)
                VALUES (?, ?, ?, ?, 1)
                """, username, passwordHash, username, role.name());
    }

    public void updatePassword(String username, String passwordHash) {
        jdbcTemplate.update("""
                UPDATE admin_user
                SET password_hash = ?, updated_at = CURRENT_TIMESTAMP
                WHERE username = ? AND status = 1
                """, passwordHash, username);
    }

    public void recordLogin(String username) {
        jdbcTemplate.update(
                "UPDATE admin_user SET last_login_at = CURRENT_TIMESTAMP WHERE username = ?", username
        );
    }

    public record AdminUserCredential(String username, String passwordHash, AdminRole role) {
    }
}
