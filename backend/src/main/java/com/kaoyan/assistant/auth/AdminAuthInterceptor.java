package com.kaoyan.assistant.auth;

import com.kaoyan.assistant.audit.DataChangeLogRepository;
import com.kaoyan.assistant.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String PRINCIPAL_ATTRIBUTE = AdminAuthInterceptor.class.getName() + ".principal";

    private static final Set<String> PROTECTED_PREFIXES = Set.of(
            "/api/schools",
            "/api/colleges",
            "/api/majors",
            "/api/sources",
            "/api/source-documents",
            "/api/admission-plans",
            "/api/admission-results",
            "/api/admission-result-imports",
            "/api/retest-rules",
            "/api/reference-books",
            "/api/adjustment-infos",
            "/api/exam-subjects",
            "/api/score-lines",
            "/api/catalog-imports",
            "/api/data-coverage/tasks",
            "/api/ai/agent/operations"
    );

    private final AdminAuthService authService;
    private final ObjectMapper objectMapper;
    private final DataChangeLogRepository changeLogRepository;

    public AdminAuthInterceptor(AdminAuthService authService, ObjectMapper objectMapper,
                                DataChangeLogRepository changeLogRepository) {
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.changeLogRepository = changeLogRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!requiresAuth(request)) {
            return true;
        }
        String token = extractBearerToken(request.getHeader("Authorization"));
        AdminPrincipal principal = authService.principalFor(token);
        if (principal == null) {
            writeFailure(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录管理端");
            return false;
        }
        AdminPermission permission = requiredPermission(request);
        if (!principal.role().allows(permission)) {
            writeFailure(response, HttpServletResponse.SC_FORBIDDEN, "当前角色无权执行此操作");
            return false;
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
        return true;
    }

    private void writeFailure(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(status, message));
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!requiresAuth(request) || response.getStatus() < 200 || response.getStatus() >= 300) {
            return;
        }
        AdminPrincipal principal = (AdminPrincipal) request.getAttribute(PRINCIPAL_ATTRIBUTE);
        String operator = principal == null ? null : principal.username();
        changeLogRepository.save(operator, request.getMethod(), request.getRequestURI(), response.getStatus());
    }

    private boolean requiresAuth(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/ai/agent/operations")) {
            return true;
        }
        if (path.startsWith("/api/admission-result-imports")) {
            return true;
        }
        if (path.equals("/api/auth/password")) {
            return true;
        }
        if (path.matches("/api/source-documents/\\d+/versions(?:/.*)?")) {
            return true;
        }
        if (path.equals("/api/source-documents/parse-tasks")) {
            return true;
        }
        if (path.equals("/api/source-documents/web-captures")) {
            return true;
        }
        if (path.startsWith("/api/source-documents/web-capture-changes")) {
            return true;
        }
        if (path.startsWith("/api/source-documents/web-capture-schedules")) {
            return true;
        }
        if (path.startsWith("/api/source-documents/publication-batches")) {
            return true;
        }
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return false;
        }
        return PROTECTED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private AdminPermission requiredPermission(HttpServletRequest request) {
        if (request.getRequestURI().equals("/api/auth/password")) {
            return AdminPermission.READ;
        }
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return AdminPermission.READ;
        }
        String path = request.getRequestURI();
        if ("DELETE".equalsIgnoreCase(method)
                || path.startsWith("/api/catalog-imports")
                || path.matches("/api/admission-result-imports/\\d+/publish")
                || path.startsWith("/api/ai/agent/operations")
                || path.startsWith("/api/source-documents/web-capture-schedules")
                || path.startsWith("/api/source-documents/publication-batches")
                || path.matches("/api/source-documents/\\d+/versions/\\d+/rollback")) {
            return AdminPermission.ADMIN;
        }
        return AdminPermission.WRITE;
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }
}
