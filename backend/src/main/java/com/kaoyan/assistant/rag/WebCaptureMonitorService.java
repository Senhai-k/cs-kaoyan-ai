package com.kaoyan.assistant.rag;

import com.kaoyan.assistant.quality.DataCollectionTarget;
import com.kaoyan.assistant.quality.DataCollectionTargetRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WebCaptureMonitorService {

    private static final int LEASE_SECONDS = 120;

    private final WebCaptureScheduleRepository scheduleRepository;
    private final DataCollectionTargetRepository targetRepository;
    private final ControlledWebFetcher fetcher;
    private final WebCaptureService captureService;

    public WebCaptureMonitorService(WebCaptureScheduleRepository scheduleRepository,
                                    DataCollectionTargetRepository targetRepository,
                                    ControlledWebFetcher fetcher,
                                    WebCaptureService captureService) {
        this.scheduleRepository = scheduleRepository;
        this.targetRepository = targetRepository;
        this.fetcher = fetcher;
        this.captureService = captureService;
    }

    public List<WebCaptureScheduleDto> schedules() {
        return scheduleRepository.findAll();
    }

    public WebCaptureScheduleDto configure(Long targetId, WebCaptureScheduleRequest request, String operator) {
        DataCollectionTarget target = targetRepository.findById(targetId);
        if (target == null) {
            throw new IllegalArgumentException("collection target not found");
        }
        if (request.enabled()) {
            fetcher.validatePublicArticleUri(target.sourceUrl());
        }
        return scheduleRepository.configure(
                targetId, request.enabled(), request.intervalHours(), normalizeOperator(operator)
        );
    }

    public WebCaptureMonitorRunResult runDue(int requestedLimit, String operator) {
        int limit = Math.max(1, Math.min(requestedLimit, 10));
        String normalizedOperator = normalizeOperator(operator);
        String owner = normalizedOperator + ":" + UUID.randomUUID();
        int completed = 0;
        int failed = 0;
        int changes = 0;
        List<Long> targetIds = new ArrayList<>();
        for (int index = 0; index < limit; index++) {
            WebCaptureScheduleRepository.ScheduleClaim claim = scheduleRepository.claimNext(owner, LEASE_SECONDS);
            if (claim == null) {
                break;
            }
            targetIds.add(claim.targetId());
            try {
                WebCaptureDraft draft = captureService.capture(claim.targetId(), normalizedOperator);
                scheduleRepository.completeSuccess(claim, owner);
                completed++;
                if (draft.changeDetected()) {
                    changes++;
                }
            } catch (RuntimeException ex) {
                scheduleRepository.completeFailure(claim, owner, ex.getMessage());
                failed++;
            }
        }
        return new WebCaptureMonitorRunResult(
                targetIds.size(), completed, failed, changes, List.copyOf(targetIds)
        );
    }

    private String normalizeOperator(String operator) {
        return operator == null || operator.isBlank() ? "system" : operator.trim();
    }
}
