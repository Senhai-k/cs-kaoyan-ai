package com.kaoyan.assistant.rag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
class DocumentParseTaskTests {

    @Autowired
    private SourceDocumentService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private OcrEngine ocrEngine;

    @Test
    @Transactional
    void sameFileHashReusesCompletedParseTask() {
        byte[] content = "2026 计算机考研招生专业目录，考试科目包含 408。".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile firstFile = new MockMultipartFile(
                "file", "catalog.txt", "text/plain", content
        );
        MockMultipartFile renamedFile = new MockMultipartFile(
                "file", "renamed.md", "text/markdown", content
        );

        ParsedSourceDocumentDraft first = service.parseTextFile(firstFile, "招生专业目录", "editor-a");
        ParsedSourceDocumentDraft reused = service.parseTextFile(renamedFile, "复试细则", "editor-b");

        assertThat(first.duplicate()).isFalse();
        assertThat(reused.duplicate()).isTrue();
        assertThat(reused.parseTaskId()).isEqualTo(first.parseTaskId());
        assertThat(reused.fileSha256()).hasSize(64).isEqualTo(first.fileSha256());
        assertThat(reused.documentType()).isEqualTo("招生专业目录");
        assertThat(service.parseTasks(10).get(0).reuseCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_parse_task WHERE file_sha256 = ?",
                Integer.class, first.fileSha256()
        )).isEqualTo(1);
    }

    @Test
    @Transactional
    void parseFailureIsRecordedWithoutCreatingDocumentOrChunk() {
        int documentsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM source_document", Integer.class);
        int chunksBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_chunk", Integer.class);
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", " \n\t ".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.parseTextFile(file, null, "auditor-test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content is empty");

        DocumentParseTaskDto failed = service.parseTasks(10).get(0);
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.errorMessage()).contains("content is empty");
        assertThat(failed.operator()).isEqualTo("auditor-test");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM source_document", Integer.class))
                .isEqualTo(documentsBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_chunk", Integer.class))
                .isEqualTo(chunksBefore);
    }

    @Test
    @Transactional
    void scannedPdfUsesConfiguredOcrEngineAndRemainsDraftOnly() throws IOException {
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdfBytes = output.toByteArray();
        }
        when(ocrEngine.isAvailable()).thenReturn(true);
        when(ocrEngine.recognizePdf(any(byte[].class))).thenReturn(new OcrEngine.OcrResult(
                "2026 年计算机学院硕士研究生复试办法，考生须准备审核材料。", "tesseract-test:chi_sim+eng"
        ));
        int documentsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM source_document", Integer.class);
        MockMultipartFile file = new MockMultipartFile(
                "file", "scanned-retest.pdf", "application/pdf", pdfBytes
        );

        ParsedSourceDocumentDraft draft = service.parseTextFile(file, "复试细则", "ocr-test");

        assertThat(draft.rawText()).contains("复试办法");
        assertThat(draft.parserVersion()).isEqualTo("tesseract-test:chi_sim+eng");
        DocumentParseTaskDto task = service.parseTasks(10).get(0);
        assertThat(task.parserType()).isEqualTo("PDF_OCR");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM source_document", Integer.class))
                .isEqualTo(documentsBefore);
    }
}
