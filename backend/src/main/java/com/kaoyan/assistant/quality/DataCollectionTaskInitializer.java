package com.kaoyan.assistant.quality;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DataCollectionTaskInitializer {

    private final DataCoverageService dataCoverageService;

    public DataCollectionTaskInitializer(DataCoverageService dataCoverageService) {
        this.dataCoverageService = dataCoverageService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeTasks() {
        dataCoverageService.synchronizeAllTasks();
    }
}
