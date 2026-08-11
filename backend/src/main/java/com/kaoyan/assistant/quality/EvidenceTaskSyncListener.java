package com.kaoyan.assistant.quality;

import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

@Component
public class EvidenceTaskSyncListener {

    private final DataCoverageService dataCoverageService;

    public EvidenceTaskSyncListener(DataCoverageService dataCoverageService) {
        this.dataCoverageService = dataCoverageService;
    }

    @EventListener
    public void onEvidenceChanged(EvidenceChangedEvent event) {
        dataCoverageService.refreshTask(event.schoolId());
    }

    @EventListener
    public void onEvidenceBatchChanged(EvidenceBatchChangedEvent event) {
        dataCoverageService.refreshTasks(event.schoolIds());
    }
}
