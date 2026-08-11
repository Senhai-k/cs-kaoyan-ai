package com.kaoyan.assistant.quality;

import java.util.List;

public record OfficialLinkCandidate(
        Long targetId,
        String title,
        String sourceUrl,
        int score,
        List<String> matchedKeywords,
        String discoveredFrom
) {
}
