package com.kaoyan.assistant.rag;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.util.function.ToDoubleFunction;

@Component
public class WebCaptureChangeMetrics implements MeterBinder {

    private final WebCaptureChangeRepository repository;

    public WebCaptureChangeMetrics(WebCaptureChangeRepository repository) {
        this.repository = repository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("cs.kaoyan.web.capture.changes.total", this,
                        metrics -> metrics.read(WebCaptureChangeSummaryDto::totalCount))
                .description("Total detected official-page content changes")
                .register(registry);
        Gauge.builder("cs.kaoyan.web.capture.changes.pending", this,
                        metrics -> metrics.read(WebCaptureChangeSummaryDto::pendingCount))
                .description("Official-page content changes pending human review")
                .register(registry);
        Gauge.builder("cs.kaoyan.web.capture.oldest.pending.age.seconds", this,
                        metrics -> metrics.read(WebCaptureChangeSummaryDto::oldestPendingAgeSeconds))
                .description("Age in seconds of the oldest pending official-page content change")
                .register(registry);
    }

    private double read(ToDoubleFunction<WebCaptureChangeSummaryDto> extractor) {
        try {
            return extractor.applyAsDouble(repository.summary());
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
    }
}
