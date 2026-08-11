package com.kaoyan.assistant.catalog;

import com.kaoyan.assistant.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog-imports")
public class Catalog408ImportController {

    private final Catalog408ImportService service;

    public Catalog408ImportController(Catalog408ImportService service) {
        this.service = service;
    }

    @PostMapping("/408")
    public ApiResponse<Catalog408ImportResult> import408(@RequestBody Catalog408ImportRequest request) {
        return ApiResponse.success(service.importBatch(request));
    }

    @GetMapping("/408/status")
    public ApiResponse<Catalog408ImportStatus> status() {
        return ApiResponse.success(service.latestStatus());
    }
}
