package com.kaoyan.assistant.recommendation;

import java.util.List;

public record RecommendationRequest(
        Integer targetScore,
        List<String> preferredProvinces,
        Boolean prefer408,
        String degreeType,
        String riskPreference,
        Integer limit
) {
}
