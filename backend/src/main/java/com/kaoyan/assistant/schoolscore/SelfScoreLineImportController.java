package com.kaoyan.assistant.schoolscore;

import com.kaoyan.assistant.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog-imports/self-score-lines")
public class SelfScoreLineImportController {

    private final SelfScoreLineImportService service;

    public SelfScoreLineImportController(SelfScoreLineImportService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<SelfScoreLineImportResult> importBatch(@RequestBody SelfScoreLineImportRequest request) {
        return ApiResponse.success(service.importBatch(request));
    }

    @GetMapping("/status")
    public ApiResponse<SelfScoreLineImportStatus> status() {
        return ApiResponse.success(service.latestStatus());
    }
}
