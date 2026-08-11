package com.kaoyan.assistant.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WebCaptureMonitorScheduler {

    private final WebCaptureMonitorService monitorService;
    private final boolean enabled;
    private final int maxPerRun;

    public WebCaptureMonitorScheduler(
            WebCaptureMonitorService monitorService,
            @Value("${app.document.web-monitor.enabled:false}") boolean enabled,
            @Value("${app.document.web-monitor.max-per-run:2}") int maxPerRun
    ) {
        this.monitorService = monitorService;
        this.enabled = enabled;
        this.maxPerRun = Math.max(1, Math.min(maxPerRun, 10));
    }

    @Scheduled(
            initialDelayString = "${app.document.web-monitor.initial-delay-ms:60000}",
            fixedDelayString = "${app.document.web-monitor.scan-delay-ms:300000}"
    )
    public void runDueCaptures() {
        if (enabled) {
            monitorService.runDue(maxPerRun, "scheduler");
        }
    }
}
