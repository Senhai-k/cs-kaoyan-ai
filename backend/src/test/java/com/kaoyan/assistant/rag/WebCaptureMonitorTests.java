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
class WebCaptureMonitorTests {

    @Autowired
    private WebCaptureMonitorService monitorService;

    @Autowired
    private WebCaptureScheduleRepository scheduleRepository;

    @Autowired
    private DataCollectionTargetRepository targetRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ControlledWebFetcher fetcher;

    @Test
    void enabledScheduleClaimsRegisteredTargetAndAdvancesNextRun() {
        String url = "https://yz.example.edu.cn/admission/2026/monitor.html";
        Long targetId = createTarget(url);
        try {
            when(fetcher.fetch(url)).thenReturn(new ControlledWebFetcher.FetchedContent(
                    url, 200, "text/html", 640L, "复试办法",
                    "计算机学院2026年硕士研究生复试办法。复试成绩和初试成绩按官方规则计算。"
            ));
            WebCaptureScheduleDto configured = monitorService.configure(
                    targetId, new WebCaptureScheduleRequest(true, 24), "admin"
            );

            WebCaptureMonitorRunResult result = monitorService.runDue(2, "manual:admin");
            WebCaptureScheduleDto completed = scheduleRepository.findByTargetId(targetId);

            assertThat(configured.enabled()).isTrue();
            assertThat(result.claimedCount()).isEqualTo(1);
            assertThat(result.completedCount()).isEqualTo(1);
            assertThat(result.failedCount()).isZero();
            assertThat(completed.lastStatus()).isEqualTo("COMPLETED");
            assertThat(completed.consecutiveFailures()).isZero();
            assertThat(completed.leaseUntil()).isNull();
            assertThat(monitorService.runDue(1, "manual:admin").claimedCount()).isZero();
        } finally {
            cleanup(targetId);
        }
    }

    @Test
    void failedScheduledCaptureUsesBackoffAndRetainsFailure() {
        String url = "https://yz.example.edu.cn/admission/2026/unavailable.html";
        Long targetId = createTarget(url);
        try {
            when(fetcher.fetch(url)).thenThrow(new ControlledWebFetcher.WebFetchException(
                    "official page returned HTTP 503", url, 503, "text/html"
            ));
            monitorService.configure(targetId, new WebCaptureScheduleRequest(true, 24), "admin");

            WebCaptureMonitorRunResult result = monitorService.runDue(1, "scheduler");
            WebCaptureScheduleDto failed = scheduleRepository.findByTargetId(targetId);

            assertThat(result.failedCount()).isEqualTo(1);
            assertThat(failed.lastStatus()).isEqualTo("FAILED");
            assertThat(failed.lastError()).contains("HTTP 503");
            assertThat(failed.consecutiveFailures()).isEqualTo(1);
            assertThat(failed.leaseUntil()).isNull();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM web_capture_task WHERE target_id = ? AND status = 'FAILED'",
                    Integer.class, targetId
            )).isEqualTo(1);
        } finally {
            cleanup(targetId);
        }
    }

    @Test
    void leasePreventsAnotherInstanceFromClaimingTheSameTarget() {
        Long targetId = createTarget("https://yz.example.edu.cn/admission/2026/lease.html");
        try {
            monitorService.configure(targetId, new WebCaptureScheduleRequest(true, 24), "admin");

            WebCaptureScheduleRepository.ScheduleClaim first = scheduleRepository.claimNext("instance-a", 120);
            WebCaptureScheduleRepository.ScheduleClaim second = scheduleRepository.claimNext("instance-b", 120);

            assertThat(first).isNotNull();
            assertThat(first.targetId()).isEqualTo(targetId);
            assertThat(second).isNull();
            scheduleRepository.completeFailure(first, "instance-a", "test release");
        } finally {
            cleanup(targetId);
        }
    }

    @Test
    void monitoringRejectsHomepageTargetsBeforeAnyNetworkRequest() {
        String url = "https://yz.example.edu.cn/";
        Long targetId = createTarget(url);
        try {
            when(fetcher.validatePublicArticleUri(url)).thenThrow(new ControlledWebFetcher.WebFetchException(
                    "replace the site homepage with an exact official article URL", url, null, null
            ));
            assertThatThrownBy(() -> monitorService.configure(
                    targetId, new WebCaptureScheduleRequest(true, 24), "admin"
            )).isInstanceOf(ControlledWebFetcher.WebFetchException.class)
                    .hasMessageContaining("exact official article URL");
            assertThat(scheduleRepository.findByTargetId(targetId)).isNull();
        } finally {
            cleanup(targetId);
        }
    }

    private Long createTarget(String url) {
        return targetRepository.create(1L, new DataCollectionTargetRequest(
                "2026年复试办法", "复试细则", 2026, url, "PENDING", "调度集成测试"
        ), false);
    }

    private void cleanup(Long targetId) {
        jdbcTemplate.update("DELETE FROM web_capture_change WHERE target_id = ?", targetId);
        jdbcTemplate.update("DELETE FROM web_capture_task WHERE target_id = ?", targetId);
        jdbcTemplate.update("DELETE FROM web_capture_schedule WHERE target_id = ?", targetId);
        targetRepository.delete(targetId);
    }
}
