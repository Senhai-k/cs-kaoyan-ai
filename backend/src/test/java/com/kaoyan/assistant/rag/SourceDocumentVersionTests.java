package com.kaoyan.assistant.rag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SourceDocumentVersionTests {

    @Autowired
    private SourceDocumentService service;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Transactional
    @Rollback
    void updateAndRollbackCreateImmutableVersionsAndRebuildChunks() {
        String sourceUrl = "https://cs.test.edu.cn/version/" + UUID.randomUUID() + "/page.htm";
        SourceDocumentDto created = service.create(request(
                "原始资料", sourceUrl, "测试大学2026年招生专业目录原始正文。".repeat(30)
        ), "version-admin");
        service.generateChunks(created.id());

        SourceDocumentDto updated = service.update(created.id(), request(
                "修改后资料", sourceUrl, "测试大学2026年复试录取细则修改正文。".repeat(30)
        ), "reviewer-a");
        service.generateChunks(updated.id());
        List<SourceDocumentVersionDto> beforeRollback = service.versions(created.id());

        SourceDocumentRollbackResult rollback = service.rollback(created.id(), 1, "reviewer-b");
        List<SourceDocumentVersionDto> versions = service.versions(created.id());
        List<DocumentChunkDto> chunks = service.chunks(created.id());

        assertThat(beforeRollback).extracting(SourceDocumentVersionDto::operation)
                .containsExactly("UPDATE", "CREATE");
        assertThat(rollback.restoredVersionNo()).isEqualTo(1);
        assertThat(rollback.createdVersionNo()).isEqualTo(3);
        assertThat(rollback.document().title()).isEqualTo("原始资料");
        assertThat(versions).extracting(SourceDocumentVersionDto::operation)
                .containsExactly("ROLLBACK", "UPDATE", "CREATE");
        assertThat(versions).extracting(SourceDocumentVersionDto::operator)
                .containsExactly("reviewer-b", "reviewer-a", "version-admin");
        assertThat(chunks).hasSize(rollback.chunkCount());
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content()).contains("招生专业目录原始正文"));
    }

    @Test
    @Transactional
    @Rollback
    void invalidRollbackVersionDoesNotModifyCurrentDocument() {
        SourceDocumentDto created = service.create(request(
                "保持不变", "https://cs.test.edu.cn/version/" + UUID.randomUUID() + "/page.htm",
                "测试大学2026年招生专业目录正文。".repeat(20)
        ), "version-admin");

        assertThatThrownBy(() -> service.rollback(created.id(), 999, "version-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version not found");

        assertThat(service.detail(created.id()).title()).isEqualTo("保持不变");
        assertThat(service.versions(created.id())).hasSize(1);
    }

    @Test
    void versionHistoryRequiresAdministratorAuthentication() throws Exception {
        mockMvc.perform(get("/api/source-documents/1/versions"))
                .andExpect(status().isUnauthorized());
    }

    private SourceDocumentRequest request(String title, String sourceUrl, String rawText) {
        return new SourceDocumentRequest(
                title, "招生专业目录", sourceUrl, 2L, 2L, 2L, 2026,
                "PUBLISHED", "OFFICIAL", rawText, "版本测试"
        );
    }
}
