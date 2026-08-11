package com.kaoyan.assistant.rag;

import com.kaoyan.assistant.quality.DataCollectionTarget;
import com.kaoyan.assistant.quality.DataCollectionTargetRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class WebCaptureService {

    private static final String EXTRACTOR_VERSION = "controlled-web-v4";

    private final DataCollectionTargetRepository targetRepository;
    private final WebCaptureTaskRepository taskRepository;
    private final WebCaptureChangeRepository changeRepository;
    private final WebCapturePersistenceService persistenceService;
    private final ControlledWebFetcher fetcher;

    public WebCaptureService(DataCollectionTargetRepository targetRepository,
                             WebCaptureTaskRepository taskRepository,
                             WebCaptureChangeRepository changeRepository,
                             WebCapturePersistenceService persistenceService,
                             ControlledWebFetcher fetcher) {
        this.targetRepository = targetRepository;
        this.taskRepository = taskRepository;
        this.changeRepository = changeRepository;
        this.persistenceService = persistenceService;
        this.fetcher = fetcher;
    }

    public WebCaptureDraft capture(Long targetId, String operator) {
        DataCollectionTarget target = targetRepository.findById(targetId);
        if (target == null) {
            throw new IllegalArgumentException("collection target not found");
        }
        String normalizedOperator = operator == null || operator.isBlank() ? "system" : operator;
        WebCaptureTaskRepository.CaptureInput input = new WebCaptureTaskRepository.CaptureInput(
                target.id(), target.sourceUrl(), EXTRACTOR_VERSION, normalizedOperator
        );
        try {
            ControlledWebFetcher.FetchedContent fetched = fetcher.fetch(target.sourceUrl());
            String sha256 = sha256(fetched.rawText());
            String title = fetched.title() == null || fetched.title().isBlank()
                    ? target.title() : fetched.title();
            if (title.length() > 255) {
                title = title.substring(0, 255);
            }
            WebCapturePersistenceService.PersistedCapture persisted = persistenceService.persist(
                    target,
                    input,
                    new WebCaptureTaskRepository.CapturedContent(
                            fetched.finalUrl(), sha256, fetched.httpStatus(), fetched.contentType(),
                            fetched.responseSize(), title, fetched.rawText()
                    )
            );
            return toDraft(target, persisted.task(), persisted.duplicate(), persisted.change());
        } catch (ControlledWebFetcher.WebFetchException ex) {
            taskRepository.saveFailure(input, ex.finalUrl(), ex.httpStatus(), ex.contentType(), ex.getMessage());
            throw ex;
        }
    }

    public List<WebCaptureTaskDto> tasks(int limit) {
        return taskRepository.findRecent(limit);
    }

    public List<WebCaptureChangeDto> changes(String status, int limit) {
        return changeRepository.findRecent(status, limit);
    }

    public WebCaptureChangeSummaryDto changeSummary() {
        return changeRepository.summary();
    }

    public WebCaptureChangeDto reviewChange(Long id, WebCaptureChangeReviewRequest request, String operator) {
        String status = request.status().trim().toUpperCase();
        if (!("ACKNOWLEDGED".equals(status) || "IGNORED".equals(status))) {
            throw new IllegalArgumentException("变更处理状态必须是 ACKNOWLEDGED 或 IGNORED");
        }
        return changeRepository.review(id, status, request.note(), operator);
    }

    private WebCaptureDraft toDraft(DataCollectionTarget target,
                                    WebCaptureTaskRepository.WebCaptureTaskRecord task,
                                    boolean duplicate,
                                    WebCaptureChangeDto change) {
        return new WebCaptureDraft(
                task.id(), target.id(), target.schoolId(), task.title(), target.documentType(),
                target.targetYear(), task.finalUrl(), task.rawText(),
                "由已登记官方 URL 受控采集生成草稿，需管理员对照原文后发布",
                task.contentSha256(), duplicate, task.extractorVersion(),
                change != null, change == null ? null : change.id()
        );
    }

    private String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
