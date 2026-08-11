package com.kaoyan.assistant.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AdminAuthService {

    private final AdminUserRepository userRepository;
    private final AdminSessionRepository sessionRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String adminUsername;
    private final String adminPassword;
    private final String editorUsername;
    private final String editorPassword;
    private final String auditorUsername;
    private final String auditorPassword;
    private final long tokenTtlSeconds;
    private final boolean syncPasswordsOnStartup;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminAuthService(AdminUserRepository userRepository,
                            AdminSessionRepository sessionRepository,
                            @Value("${app.admin.username:admin}") String adminUsername,
                            @Value("${app.admin.password:}") String adminPassword,
                            @Value("${app.admin.editor.username:}") String editorUsername,
                            @Value("${app.admin.editor.password:}") String editorPassword,
                            @Value("${app.admin.auditor.username:}") String auditorUsername,
                            @Value("${app.admin.auditor.password:}") String auditorPassword,
                            @Value("${app.admin.token-ttl-seconds:86400}") long tokenTtlSeconds,
                            @Value("${app.admin.sync-password-on-startup:false}") boolean syncPasswordsOnStartup) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.editorUsername = editorUsername;
        this.editorPassword = editorPassword;
        this.auditorUsername = auditorUsername;
        this.auditorPassword = auditorPassword;
        this.tokenTtlSeconds = Math.max(1, tokenTtlSeconds);
        this.syncPasswordsOnStartup = syncPasswordsOnStartup;
    }

    @PostConstruct
    void initializeConfiguredUsers() {
        syncConfiguredUser(adminUsername, adminPassword, AdminRole.ADMIN, true);
        syncConfiguredUser(editorUsername, editorPassword, AdminRole.DATA_EDITOR, false);
        syncConfiguredUser(auditorUsername, auditorPassword, AdminRole.AUDITOR, false);
        sessionRepository.deleteExpired(Instant.now());
    }

    public AdminLoginResponse login(AdminLoginRequest request) {
        AdminUserRepository.AdminUserCredential user = userRepository.findActiveByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = generateToken();
        long expiresAt = Instant.now().plusSeconds(tokenTtlSeconds).getEpochSecond();
        sessionRepository.create(hashToken(token), user.username(), user.role(), Instant.ofEpochSecond(expiresAt));
        userRepository.recordLogin(user.username());
        return new AdminLoginResponse(token, user.username(), user.role().name(), expiresAt);
    }

    public boolean isValid(String token) {
        return principalFor(token) != null;
    }

    public AdminPrincipal principalFor(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Instant now = Instant.now();
        String tokenHash = hashToken(token);
        AdminPrincipal principal = sessionRepository.findActive(tokenHash, now);
        if (principal != null) {
            sessionRepository.touch(tokenHash, now);
        }
        return principal;
    }

    public String usernameFor(String token) {
        AdminPrincipal principal = principalFor(token);
        return principal == null ? null : principal.username();
    }

    public AdminRole roleFor(String token) {
        AdminPrincipal principal = principalFor(token);
        return principal == null ? null : principal.role();
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionRepository.revoke(hashToken(token), Instant.now());
        }
    }

    @Transactional
    public void changePassword(String token, AdminPasswordChangeRequest request) {
        AdminPrincipal principal = principalFor(token);
        if (principal == null) {
            throw new IllegalArgumentException("登录已失效，请重新登录");
        }
        AdminUserRepository.AdminUserCredential user = userRepository.findActiveByUsername(principal.username());
        if (user == null || !passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
            throw new IllegalArgumentException("当前密码错误");
        }
        if (passwordEncoder.matches(request.newPassword(), user.passwordHash())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        boolean hasLetter = request.newPassword().chars().anyMatch(Character::isLetter);
        boolean hasDigit = request.newPassword().chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("新密码必须同时包含字母和数字");
        }
        userRepository.updatePassword(principal.username(), passwordEncoder.encode(request.newPassword()));
        sessionRepository.revokeAllForUser(principal.username(), Instant.now());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private void syncConfiguredUser(String username, String password, AdminRole role, boolean required) {
        boolean usernameBlank = username == null || username.isBlank();
        boolean passwordBlank = password == null || password.isBlank();
        if (usernameBlank && passwordBlank && !required) {
            return;
        }
        if (usernameBlank || passwordBlank) {
            throw new IllegalStateException(role + " requires both username and password");
        }
        if (password.length() < 8) {
            throw new IllegalStateException(role + " password must contain at least 8 characters");
        }
        userRepository.ensureConfiguredUser(
                username.trim(), passwordEncoder.encode(password), role, syncPasswordsOnStartup
        );
    }
}
