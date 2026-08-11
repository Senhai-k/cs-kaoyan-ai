package com.kaoyan.assistant.auth;

public record AdminLoginResponse(String token, String username, String role, long expiresAt) {
}
