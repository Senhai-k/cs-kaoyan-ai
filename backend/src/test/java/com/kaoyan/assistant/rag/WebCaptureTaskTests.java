package com.kaoyan.assistant.rag;

import com.kaoyan.assistant.quality.DataCollectionTargetRepository;
import com.kaoyan.assistant.quality.DataCollectionTargetRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class WebCaptureTaskTests {

    @Autowired
    private WebCaptureService service;

    @Autowired
    private DataCollectionTargetRepository targetRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ControlledWebFetcher fetcher;

    @Test
    void registeredOfficialTargetCreatesReusableDraftWithoutPublishing() {
        String url = "https://yz.example.edu.cn/admission/2026/retest.html";
        Long targetId = createTarget(url);
        int documentsBefore = count("source_document");
        int chunksBefore = count("document_chunk");
        try {
            when(fetcher.fetch(url)).thenReturn(new ControlledWebFetcher.FetchedContent(
                    url, 200, "text/html; charset=utf-8", 512L, "2026年复试办法",
                    "计算机学院 2026 年硕士研究生复试办法。考生须按官方通知准备材料并参加复试。"
            ));

            WebCaptureDraft first = service.capture(targetId, "admin");
            WebCaptureDraft second = service.capture(targetId, "admin");

            assertThat(first.duplicate()).isFalse();
            assertThat(first.changeDetected()).isFalse();
            assertThat(second.duplicate()).isTrue();
            assertThat(second.changeDetected()).isFalse();
            assertThat(first.schoolId()).isEqualTo(1L);
            assertThat(first.sourceUrl()).isEqualTo(url);
            assertThat(count("source_document")).isEqualTo(documentsBefore);
            assertThat(count("document_chunk")).isEqualTo(chunksBefore);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT reuse_count FROM web_capture_task WHERE target_id = ?",
                    Integer.class, targetId
            )).isEqualTo(1);
        } finally {
            cleanup(targetId);
        }
    }

    @Test
    void changedAndRevertedContentCreateReviewableEvents() {
        String url = "https://yz.example.edu.cn/admission/2026/change.html";
        Long targetId = createTarget(url);
        WebCaptureChangeSummaryDto before = service.changeSummary();
        String original = "计算机学院2026年复试办法。复试成绩占总成绩百分之四十。考生须携带身份证参加现场复试。";
        String changed = "计算机学院2026年复试办法。复试成绩占总成绩百分之五十。考生须携带身份证和准考证参加现场复试。";
        try {
            when(fetcher.fetch(url)).thenReturn(
                    new ControlledWebFetcher.FetchedContent(url, 200, "text/html", 500L, "复试办法", original),
                    new ControlledWebFetcher.FetchedContent(url, 200, "text/html", 510L, "复试办法", changed),
                    new ControlledWebFetcher.FetchedContent(url, 200, "text/html", 500L, "复试办法", original)
            );

            WebCaptureDraft baseline = service.capture(targetId, "collector");
            WebCaptureDraft update = service.capture(targetId, "collector");
            WebCaptureDraft reverted = service.capture(targetId, "collector");

            assertThat(baseline.changeDetected()).isFalse();
            assertThat(update.changeDetected()).isTrue();
            assertThat(update.changeId()).isNotNull();
            assertThat(reverted.duplicate()).isTrue();
            assertThat(reverted.changeDetected()).isTrue();
            assertThat(count("web_capture_change")).isEqualTo(2);

            WebCaptureChangeSummaryDto detectedSummary = service.changeSummary();
            assertThat(detectedSummary.totalCount()).isEqualTo(before.totalCount() + 2);
            assertThat(detectedSummary.pendingCount()).isEqualTo(before.pendingCount() + 2);
            assertThat(detectedSummary.maxChangeRatio()).isPositive();
            assertThat(detectedSummary.oldestPendingAt()).isNotBlank();

            WebCaptureChangeDto pending = service.changes("PENDING_REVIEW", 20).stream()
                    .filter(change -> change.id().equals(update.changeId()))
                    .findFirst().orElseThrow();
            assertThat(pending.addedLineCount()).isPositive();
            assertThat(pending.removedLineCount()).isPositive();
            assertThat(pending.changeRatio()).isGreaterThan(0);

            WebCaptureChangeDto reviewed = service.reviewChange(
                    update.changeId(), new WebCaptureChangeReviewRequest("ACKNOWLEDGED", "确认官网规则已调整"),
                    "reviewer"
            );
            assertThat(reviewed.status()).isEqualTo("ACKNOWLEDGED");
            assertThat(reviewed.reviewer()).isEqualTo("reviewer");
            WebCaptureChangeSummaryDto reviewedSummary = service.changeSummary();
            assertThat(reviewedSummary.pendingCount()).isEqualTo(before.pendingCount() + 1);
            assertThat(reviewedSummary.acknowledgedCount()).isEqualTo(before.acknowledgedCount() + 1);
            assertThatThrownBy(() -> service.reviewChange(
                    update.changeId(), new WebCaptureChangeReviewRequest("IGNORED", "重复处理"), "reviewer"
            )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已处理");
        } finally {
            cleanup(targetId);
        }
    }

    @Test
    void failedFetchIsRetainedAsOperationalRecord() {
        String url = "https://yz.example.edu.cn/admission/2026/empty.html";
        Long targetId = createTarget(url);
        try {
            when(fetcher.fetch(url)).thenThrow(new ControlledWebFetcher.WebFetchException(
                    "official page has too little extractable text", url, 200, "text/html"
            ));

            assertThatThrownBy(() -> service.capture(targetId, "auditor"))
                    .isInstanceOf(ControlledWebFetcher.WebFetchException.class);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM web_capture_task WHERE target_id = ?",
                    String.class, targetId
            )).isEqualTo("FAILED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT error_message FROM web_capture_task WHERE target_id = ?",
                    String.class, targetId
            )).contains("too little");
        } finally {
            cleanup(targetId);
        }
    }

    private Long createTarget(String url) {
        return targetRepository.create(1L, new DataCollectionTargetRequest(
                "2026年复试办法", "复试细则", 2026, url, "PENDING", "集成测试"
        ), false);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void cleanup(Long targetId) {
        jdbcTemplate.update("DELETE FROM web_capture_change WHERE target_id = ?", targetId);
        jdbcTemplate.update("DELETE FROM web_capture_task WHERE target_id = ?", targetId);
        targetRepository.delete(targetId);
    }
}
