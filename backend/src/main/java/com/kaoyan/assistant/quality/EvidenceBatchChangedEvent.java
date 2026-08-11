package com.kaoyan.assistant.quality;

import java.util.Set;

public record EvidenceBatchChangedEvent(Set<Long> schoolIds) {
    public EvidenceBatchChangedEvent {
        schoolIds = schoolIds == null ? Set.of() : Set.copyOf(schoolIds);
    }
}
