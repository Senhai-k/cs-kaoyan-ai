package com.kaoyan.assistant.rag;

import com.kaoyan.assistant.auth.AdminAuthService;
import com.kaoyan.assistant.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/source-documents")
public class SourceDocumentController {

    private final SourceDocumentService service;
    private final WebCaptureService webCaptureService;
    private final DocumentPublicationBatchService publicationBatchService;
    private final WebCaptureMonitorService monitorService;
    private final AdminAuthService authService;

    public SourceDocumentController(SourceDocumentService service, WebCaptureService webCaptureService,
                                    DocumentPublicationBatchService publicationBatchService,
                                    WebCaptureMonitorService monitorService,
                                    AdminAuthService authService) {
        this.service = service;
        this.webCaptureService = webCaptureService;
        this.publicationBatchService = publicationBatchService;
        this.monitorService = monitorService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<SourceDocumentDto>> list(@RequestParam(required = false) Long schoolId,
                                                     @RequestParam(required = false) String auditStatus,
                                                     HttpServletRequest request) {
        return ApiResponse.success(service.list(schoolId, resolveAuditStatusFilter(auditStatus, request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<SourceDocumentDto> detail(@PathVariable Long id) {
        SourceDocumentDto document = service.detail(id);
        return document == null ? ApiResponse.failure(404, "source document not found") : ApiResponse.success(document);
    }

    @PostMapping
    public ApiResponse<SourceDocumentDto> create(@Valid @RequestBody SourceDocumentRequest request,
                                                 HttpServletRequest httpRequest) {
        rejectDirectPublication(List.of(request));
        return ApiResponse.success(service.create(request, operator(httpRequest)));
    }

    @PostMapping("/batch")
    public ApiResponse<SourceDocumentBatchImportResult> batchImport(@Valid @RequestBody List<SourceDocumentRequest> requests,
                                                                    @RequestParam(defaultValue = "true") boolean generateChunks,
                                                                    HttpServletRequest httpRequest) {
        rejectDirectPublication(requests);
        return ApiResponse.success(service.batchImport(requests, generateChunks, operator(httpRequest)));
    }

    @PostMapping("/quality-check")
    public ApiResponse<SourceDocumentQualityReport> qualityCheck(@RequestBody List<SourceDocumentRequest> requests) {
        return ApiResponse.success(service.qualityCheck(requests));
    }

    @PostMapping("/parse")
    public ApiResponse<ParsedSourceDocumentDraft> parse(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(required = false) String documentType,
                                                        HttpServletRequest request) {
        return ApiResponse.success(service.parseTextFile(file, documentType, operator(request)));
    }

    @GetMapping("/parse-tasks")
    public ApiResponse<List<DocumentParseTaskDto>> parseTasks(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(service.parseTasks(limit));
    }

    @PostMapping("/web-captures")
    public ApiResponse<WebCaptureDraft> captureWeb(@Valid @RequestBody WebCaptureRequest captureRequest,
                                                   HttpServletRequest request) {
        return ApiResponse.success(webCaptureService.capture(captureRequest.targetId(), operator(request)));
    }

    @GetMapping("/web-captures")
    public ApiResponse<List<WebCaptureTaskDto>> webCaptureTasks(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(webCaptureService.tasks(limit));
    }

    @GetMapping("/web-capture-changes")
    public ApiResponse<List<WebCaptureChangeDto>> webCaptureChanges(
            @RequestParam(defaultValue = "PENDING_REVIEW") String status,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.success(webCaptureService.changes(status, limit));
    }

    @GetMapping("/web-capture-changes/summary")
    public ApiResponse<WebCaptureChangeSummaryDto> webCaptureChangeSummary() {
        return ApiResponse.success(webCaptureService.changeSummary());
    }

    @PostMapping("/web-capture-changes/{changeId}/review")
    public ApiResponse<WebCaptureChangeDto> reviewWebCaptureChange(
            @PathVariable Long changeId,
            @Valid @RequestBody WebCaptureChangeReviewRequest reviewRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(webCaptureService.reviewChange(
                changeId, reviewRequest, operator(request)
        ));
    }

    @GetMapping("/web-capture-schedules")
    public ApiResponse<List<WebCaptureScheduleDto>> webCaptureSchedules() {
        return ApiResponse.success(monitorService.schedules());
    }

    @PutMapping("/web-capture-schedules/{targetId}")
    public ApiResponse<WebCaptureScheduleDto> configureWebCaptureSchedule(
            @PathVariable Long targetId,
            @Valid @RequestBody WebCaptureScheduleRequest scheduleRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(monitorService.configure(targetId, scheduleRequest, operator(request)));
    }

    @PostMapping("/web-capture-schedules/run-due")
    public ApiResponse<WebCaptureMonitorRunResult> runDueWebCaptureSchedules(
            @RequestParam(defaultValue = "2") int limit,
            HttpServletRequest request
    ) {
        return ApiResponse.success(monitorService.runDue(limit, operator(request)));
    }

    @PostMapping("/publication-batches")
    public ApiResponse<DocumentPublicationBatchResult> publishBatch(
            @Valid @RequestBody DocumentPublicationBatchRequest batchRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(publicationBatchService.publish(batchRequest, operator(request)));
    }

    @GetMapping("/publication-batches")
    public ApiResponse<List<DocumentPublicationBatchDto>> publicationBatches(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.success(publicationBatchService.batches(limit));
    }

    @PostMapping("/publication-batches/{batchId}/rollback")
    public ApiResponse<DocumentPublicationBatchResult> rollbackPublicationBatch(
            @PathVariable Long batchId,
            @Valid @RequestBody DocumentPublicationRollbackRequest rollbackRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(publicationBatchService.rollback(
                batchId, rollbackRequest, operator(request)
        ));
    }

    @PutMapping("/{id}")
    public ApiResponse<SourceDocumentDto> update(@PathVariable Long id,
                                                 @Valid @RequestBody SourceDocumentRequest request,
                                                 HttpServletRequest httpRequest) {
        rejectDirectPublication(List.of(request));
        return ApiResponse.success(service.update(id, request, operator(httpRequest)));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<SourceDocumentVersionDto>> versions(@PathVariable Long id) {
        return ApiResponse.success(service.versions(id));
    }

    @PostMapping("/{id}/versions/{versionNo}/rollback")
    public ApiResponse<SourceDocumentRollbackResult> rollback(@PathVariable Long id,
                                                              @PathVariable Integer versionNo,
                                                              HttpServletRequest httpRequest) {
        return ApiResponse.success(service.rollback(id, versionNo, operator(httpRequest)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/chunks")
    public ApiResponse<List<DocumentChunkDto>> generateChunks(@PathVariable Long id) {
        return ApiResponse.success(service.generateChunks(id));
    }

    @GetMapping("/{id}/chunks")
    public ApiResponse<List<DocumentChunkDto>> chunks(@PathVariable Long id) {
        return ApiResponse.success(service.chunks(id));
    }

    @GetMapping("/chunks/search")
    public ApiResponse<List<DocumentChunkDto>> searchChunks(@RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) Long schoolId,
                                                           @RequestParam(required = false) Integer year,
                                                           @RequestParam(required = false) String documentType,
                                                           @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(service.searchChunks(keyword, schoolId, year, documentType, limit));
    }

    private String resolveAuditStatusFilter(String auditStatus, HttpServletRequest request) {
        if ("ALL".equalsIgnoreCase(auditStatus) && !isAdminRequest(request)) {
            return null;
        }
        return auditStatus;
    }

    private void rejectDirectPublication(List<SourceDocumentRequest> requests) {
        if (requests != null && requests.stream().anyMatch(
                request -> request != null && "PUBLISHED".equalsIgnoreCase(request.auditStatus())
        )) {
            throw new IllegalArgumentException("管理端直接写入不能发布资料，请保存为草稿或待审核后使用发布批次");
        }
    }

    private boolean isAdminRequest(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        return authService.isValid(authorization.substring("Bearer ".length()));
    }

    private String operator(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "system";
        }
        String username = authService.usernameFor(authorization.substring("Bearer ".length()));
        return username == null ? "system" : username;
    }
}
