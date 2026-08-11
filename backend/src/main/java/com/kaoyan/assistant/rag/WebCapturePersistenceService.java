package com.kaoyan.assistant.rag;

import com.kaoyan.assistant.quality.DataCollectionTarget;
import com.kaoyan.assistant.quality.DataCollectionTargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class WebCapturePersistenceService {

    private static final int EXCERPT_LIMIT = 1800;

    private final DataCollectionTargetRepository targetRepository;
    private final WebCaptureTaskRepository taskRepository;
    private final WebCaptureChangeRepository changeRepository;

    public WebCapturePersistenceService(DataCollectionTargetRepository targetRepository,
                                        WebCaptureTaskRepository taskRepository,
                                        WebCaptureChangeRepository changeRepository) {
        this.targetRepository = targetRepository;
        this.taskRepository = taskRepository;
        this.changeRepository = changeRepository;
    }

    @Transactional
    public PersistedCapture persist(DataCollectionTarget requestedTarget,
                                    WebCaptureTaskRepository.CaptureInput input,
                                    WebCaptureTaskRepository.CapturedContent content) {
        DataCollectionTarget lockedTarget = targetRepository.findByIdForUpdate(requestedTarget.id());
        if (lockedTarget == null || !lockedTarget.sourceUrl().equals(requestedTarget.sourceUrl())) {
            throw new IllegalArgumentException("采集目标已修改，请重新抓取");
        }

        WebCaptureTaskRepository.WebCaptureTaskRecord previous = taskRepository.findLatestCompleted(lockedTarget.id());
        WebCaptureTaskRepository.WebCaptureTaskRecord existing = taskRepository.findCompleted(
                lockedTarget.id(), content.sha256()
        );
        boolean duplicate = existing != null;
        WebCaptureTaskRepository.WebCaptureTaskRecord current = duplicate
                ? taskRepository.markReused(lockedTarget.id(), content.sha256(), input.operator())
                : taskRepository.saveSuccess(input, content);

        WebCaptureChangeDto change = null;
        if (previous != null && !previous.contentSha256().equals(current.contentSha256())) {
            TextDifference difference = compare(previous.rawText(), current.rawText());
            change = changeRepository.create(new WebCaptureChangeRepository.ChangeInput(
                    lockedTarget.id(), previous.id(), current.id(), previous.contentSha256(),
                    current.contentSha256(), previous.extractedLength(), current.extractedLength(),
                    difference.addedCount(), difference.removedCount(), difference.ratio(),
                    difference.previousExcerpt(), difference.currentExcerpt()
            ));
        }
        return new PersistedCapture(current, duplicate, change);
    }

    private TextDifference compare(String previousText, String currentText) {
        List<String> previous = segments(previousText);
        List<String> current = segments(currentText);
        Set<String> previousSet = new LinkedHashSet<>(previous);
        Set<String> currentSet = new LinkedHashSet<>(current);
        List<String> removed = previous.stream().filter(line -> !currentSet.contains(line)).distinct().toList();
        List<String> added = current.stream().filter(line -> !previousSet.contains(line)).distinct().toList();
        int denominator = Math.max(1, previousSet.size() + currentSet.size());
        double ratio = Math.min(1.0, (double) (added.size() + removed.size()) / denominator);
        return new TextDifference(
                added.size(), removed.size(), Math.round(ratio * 10000.0) / 10000.0,
                excerpt(removed), excerpt(added)
        );
    }

    private List<String> segments(String text) {
        String normalized = text == null ? "" : text.trim();
        List<String> result = new ArrayList<>();
        for (String part : normalized.split("(?<=[。！？；.!?])\\s*|\\R+")) {
            String value = part.trim().replaceAll("\\s+", " ");
            if (value.length() < 4) {
                continue;
            }
            if (value.length() <= 200) {
                result.add(value);
                continue;
            }
            for (int start = 0; start < value.length(); start += 160) {
                result.add(value.substring(start, Math.min(start + 160, value.length())));
            }
        }
        return result;
    }

    private String excerpt(List<String> lines) {
        String text = String.join("\n", lines.stream().limit(8).toList());
        return text.length() <= EXCERPT_LIMIT ? text : text.substring(0, EXCERPT_LIMIT);
    }

    public record PersistedCapture(
            WebCaptureTaskRepository.WebCaptureTaskRecord task,
            boolean duplicate,
            WebCaptureChangeDto change
    ) {
    }

    private record TextDifference(
            int addedCount,
            int removedCount,
            double ratio,
            String previousExcerpt,
            String currentExcerpt
    ) {
    }
}
