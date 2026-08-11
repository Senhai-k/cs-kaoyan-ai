package com.kaoyan.assistant.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.admin.editor.username=editor",
        "app.admin.editor.password=editor-pass-123",
        "app.admin.auditor.username=auditor",
        "app.admin.auditor.password=auditor-pass-123"
})
@AutoConfigureMockMvc
class AdminRbacTests {

    private static final String ADMIN_PASSWORD = "test-admin-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminUserRepository userRepository;

    @Autowired
    private AdminSessionRepository sessionRepository;

    @Autowired
    private AdminAuthService authService;

    @Test
    void configuredAccountsUseBcryptAndReturnTheirRole() throws Exception {
        String editorToken = login("editor", "editor-pass-123", "DATA_EDITOR");
        String auditorToken = login("auditor", "auditor-pass-123", "AUDITOR");

        assertThat(editorToken).isNotBlank();
        assertThat(auditorToken).isNotBlank();
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM admin_user WHERE username = 'editor'", String.class
        );
        assertThat(passwordHash).startsWith("$2").doesNotContain("editor-pass-123");
    }

    @Test
    void dataEditorCanWriteButCannotDeleteRollbackOrOperateAgent() throws Exception {
        String token = login("editor", "editor-pass-123", "DATA_EDITOR");

        mockMvc.perform(post("/api/source-documents/quality-check")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/source-documents/999999")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(post("/api/source-documents/1/versions/1/rollback")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/source-documents/publication-batches")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIds\":[1],\"reason\":\"editor cannot publish\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ai/agent/operations/index-sync")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/source-documents/web-capture-schedules/1")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"intervalHours\":24}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void auditorCanReadProtectedHistoryButCannotWrite() throws Exception {
        String token = login("auditor", "auditor-pass-123", "AUDITOR");

        mockMvc.perform(get("/api/source-documents/1/versions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/source-documents/quality-check")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("当前角色无权执行此操作"));
    }

    @Test
    void admissionResultImportSeparatesReadDraftAndPublishPermissions() throws Exception {
        String editorToken = login("editor", "editor-pass-123", "DATA_EDITOR");
        String auditorToken = login("auditor", "auditor-pass-123", "AUDITOR");
        String adminToken = login("admin", ADMIN_PASSWORD, "ADMIN");

        mockMvc.perform(get("/api/admission-result-imports"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admission-result-imports")
                        .header("Authorization", bearer(auditorToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admission-result-imports/preview")
                        .header("Authorization", bearer(auditorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admission-result-imports/preview")
                        .header("Authorization", bearer(editorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admission-result-imports/999999/publish")
                        .header("Authorization", bearer(editorToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admission-result-imports/999999/publish")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void administratorRetainsDestructivePermissionAndAnonymousRequestIsUnauthorized() throws Exception {
        String token = login("admin", ADMIN_PASSWORD, "ADMIN");

        mockMvc.perform(delete("/api/source-documents/999999")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/source-documents/1/versions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/api/source-documents/publication-batches"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/source-documents/web-capture-changes"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/source-documents/web-capture-changes/summary"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/source-documents/web-capture-schedules"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/source-documents")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"绕过发布","documentType":"招生简章","sourceUrl":"https://test.edu.cn/a",
                                 "schoolId":2,"year":2026,"auditStatus":"PUBLISHED","sourceReliability":"OFFICIAL",
                                 "rawText":"这是一段用于验证直接发布会被拒绝的完整资料正文，长度超过三十个字符。"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("发布批次")));
    }

    @Test
    void weakConfiguredPasswordFailsFast() {
        AdminAuthService service = new AdminAuthService(
                mock(AdminUserRepository.class), mock(AdminSessionRepository.class),
                "admin", "short", "", "", "", "", 3600, false
        );

        assertThatThrownBy(service::initializeConfiguredUsers)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 8 characters");
    }

    @Test
    void tokenCanBeResolvedByAnotherServiceInstanceAndLogoutIsShared() throws Exception {
        String token = login("editor", "editor-pass-123", "DATA_EDITOR");
        AdminAuthService secondInstance = new AdminAuthService(
                userRepository, sessionRepository,
                "admin", ADMIN_PASSWORD, "", "", "", "", 3600, false
        );

        assertThat(secondInstance.principalFor(token))
                .extracting(AdminPrincipal::username, AdminPrincipal::role)
                .containsExactly("editor", AdminRole.DATA_EDITOR);
        secondInstance.logout(token);
        assertThat(secondInstance.principalFor(token)).isNull();
    }

    @Test
    void passwordChangePersistsAndRevokesEverySession() throws Exception {
        String firstToken = login("admin", ADMIN_PASSWORD, "ADMIN");
        String secondToken = login("admin", ADMIN_PASSWORD, "ADMIN");
        try {
            mockMvc.perform(post("/api/auth/password")
                            .header("Authorization", bearer(firstToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword":"test-admin-password","newPassword":"Rotated-pass-2026"}
                                    """))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/source-documents/1/versions")
                            .header("Authorization", bearer(firstToken)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/source-documents/1/versions")
                            .header("Authorization", bearer(secondToken)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"test-admin-password\"}"))
                    .andExpect(status().isBadRequest());
            authService.initializeConfiguredUsers();
            login("admin", "Rotated-pass-2026", "ADMIN");
        } finally {
            jdbcTemplate.update(
                    "UPDATE admin_user SET password_hash = ? WHERE username = 'admin'",
                    new BCryptPasswordEncoder().encode(ADMIN_PASSWORD)
            );
        }
    }

    private String login(String username, String password, String expectedRole) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminLoginRequest(username, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value(expectedRole))
                .andReturn().getResponse().getContentAsString();
        JsonNode payload = objectMapper.readTree(response);
        return payload.path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
