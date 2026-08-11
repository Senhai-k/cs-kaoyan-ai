package com.kaoyan.assistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InternalAgentEvidenceControllerTests {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    private Long schoolId;
    private Long targetId;
    private String sourceUrl;

    @BeforeEach
    void createTarget() {
        schoolId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM school", Long.class);
        sourceUrl = "https://cs.test.edu.cn/agent/" + UUID.randomUUID() + "/page.htm";
        jdbcTemplate.update("""
                INSERT INTO data_collection_target (
                  school_id, title, document_type, target_year, source_url, status, note, system_generated
                ) VALUES (?, 'Agent测试资料', '复试录取细则', 2026, ?, 'PENDING', '', 0)
                """, schoolId, sourceUrl);
        targetId = jdbcTemplate.queryForObject(
                "SELECT id FROM data_collection_target WHERE source_url = ?", Long.class, sourceUrl
        );
    }

    @Test
    void rejectsMissingOrWrongInternalToken() throws Exception {
        String body = requestBody();
        mockMvc.perform(post("/api/internal/agent/evidence")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(post("/api/internal/agent/evidence")
                        .header("X-Agent-Service-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectsWorkflowTelemetryFromAnonymousAccess() throws Exception {
        mockMvc.perform(get("/api/ai/agent/operations/coverage-workflows/runs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void publishesIdempotentlyGeneratesChunksAndVerifiesTarget() throws Exception {
        String firstBody = mockMvc.perform(post("/api/internal/agent/evidence")
                        .header("X-Agent-Service-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.chunkCount").isNumber())
                .andExpect(jsonPath("$.data.targetStatus").value("VERIFIED"))
                .andReturn().getResponse().getContentAsString();
        JsonNode first = objectMapper.readTree(firstBody).path("data");

        String secondBody = mockMvc.perform(post("/api/internal/agent/evidence")
                        .header("X-Agent-Service-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(false))
                .andReturn().getResponse().getContentAsString();
        JsonNode second = objectMapper.readTree(secondBody).path("data");

        assertThat(second.path("documentId").asLong()).isEqualTo(first.path("documentId").asLong());
        assertThat(first.path("chunkCount").asInt()).isGreaterThan(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM source_document WHERE source_url = ?", Integer.class, sourceUrl
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM data_collection_target WHERE id = ?", String.class, targetId
        )).isEqualTo("VERIFIED");
    }

    @Test
    void rejectsDocumentYearOrTypeThatDoesNotMatchCollectionTarget() throws Exception {
        mockMvc.perform(post("/api/internal/agent/evidence")
                        .header("X-Agent-Service-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(2025, "复试录取细则")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/internal/agent/evidence")
                        .header("X-Agent-Service-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(2026, "招生专业目录")))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM source_document WHERE source_url = ?", Integer.class, sourceUrl
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM data_collection_target WHERE id = ?", String.class, targetId
        )).isEqualTo("PENDING");
    }

    @Test
    void rejectsExistingSourceUrlOwnedByAnotherSchoolAndRollsBackTarget() throws Exception {
        Long otherSchoolId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM school", Long.class);
        assertThat(otherSchoolId).isNotEqualTo(schoolId);
        jdbcTemplate.update("""
                INSERT INTO source_document (
                  title, document_type, source_url, school_id, year,
                  audit_status, source_reliability, raw_text, remark
                ) VALUES ('其他学校资料', '复试录取细则', ?, ?, 2026,
                  'PUBLISHED', 'OFFICIAL', '其他学校资料正文', '冲突测试')
                """, sourceUrl, otherSchoolId);

        mockMvc.perform(post("/api/internal/agent/evidence")
                        .header("X-Agent-Service-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT school_id FROM source_document WHERE source_url = ?", Long.class, sourceUrl
        )).isEqualTo(otherSchoolId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM data_collection_target WHERE id = ?", String.class, targetId
        )).isEqualTo("PENDING");
    }

    private String requestBody() throws Exception {
        return requestBody(2026, "复试录取细则");
    }

    private String requestBody(int year, String documentType) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "targetId", targetId,
                "feedback", "已人工核对",
                "document", Map.of(
                        "title", "2026年Agent测试复试细则",
                        "documentType", documentType,
                        "sourceUrl", sourceUrl,
                        "schoolId", schoolId,
                        "year", year,
                        "auditStatus", "PUBLISHED",
                        "sourceReliability", "OFFICIAL",
                        "rawText", "这是用于验证自主数据工作流的官方资料正文。".repeat(40),
                        "remark", "自动采集后经人工审核"
                )
        ));
    }
}
