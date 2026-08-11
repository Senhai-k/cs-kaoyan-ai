package com.kaoyan.assistant.recommendation;

import com.kaoyan.assistant.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping
    public ApiResponse<List<RecommendationItem>> recommend(@RequestBody(required = false) RecommendationRequest request) {
        return ApiResponse.success(recommendationService.recommend(request));
    }
}
