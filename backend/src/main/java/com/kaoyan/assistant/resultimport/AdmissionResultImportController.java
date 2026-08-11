package com.kaoyan.assistant.resultimport;

import com.kaoyan.assistant.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admission-result-imports")
public class AdmissionResultImportController {

    private final AdmissionResultImportService service;

    public AdmissionResultImportController(AdmissionResultImportService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AdmissionResultImportBatchDto>> list() {
        return ApiResponse.success(service.list());
    }

    @PostMapping("/preview")
    public ApiResponse<AdmissionResultImportPreview> preview(@RequestBody AdmissionResultImportRequest request) {
        return ApiResponse.success(service.preview(request));
    }

    @PostMapping
    public ApiResponse<AdmissionResultImportDraft> createDraft(@RequestBody AdmissionResultImportRequest request) {
        return ApiResponse.success(service.createDraft(request));
    }

    @PostMapping("/{batchId}/publish")
    public ApiResponse<AdmissionResultImportPublishResult> publish(@PathVariable long batchId) {
        return ApiResponse.success(service.publish(batchId));
    }
}
