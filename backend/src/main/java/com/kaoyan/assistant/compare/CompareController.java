package com.kaoyan.assistant.compare;

import com.kaoyan.assistant.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/compare")
public class CompareController {

    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    @GetMapping
    public ApiResponse<CompareResult> compare(@RequestParam List<Long> ids) {
        if (ids.size() < 2) {
            return ApiResponse.failure(400, "at least two schools are required");
        }
        return ApiResponse.success(compareService.compare(ids));
    }
}
