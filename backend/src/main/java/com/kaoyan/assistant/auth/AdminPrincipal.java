package com.kaoyan.assistant.auth;

public record AdminPrincipal(String username, AdminRole role, long expiresAt) {
}
