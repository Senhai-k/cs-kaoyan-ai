package com.kaoyan.assistant.ai;

import com.kaoyan.assistant.rag.DocumentChunkDto;
import com.kaoyan.assistant.rag.DocumentChunkRepository;
import com.kaoyan.assistant.rag.SourceDocumentDto;
import com.kaoyan.assistant.rag.SourceDocumentRepository;
import com.kaoyan.assistant.school.SchoolService;
import com.kaoyan.assistant.school.SchoolSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private final AiProvider aiProvider;
    private final AiConversationRepository conversationRepository;
    private final DocumentChunkRepository chunkRepository;
    private final SourceDocumentRepository documentRepository;
    private final SchoolService schoolService;
    private final boolean agentProvider;

    public AiChatService(AiProvider aiProvider, AiConversationRepository conversationRepository,
                         DocumentChunkRepository chunkRepository, SourceDocumentRepository documentRepository,
                         SchoolService schoolService,
                         @Value("${app.ai.provider:local}") String providerName) {
        this.aiProvider = aiProvider;
        this.conversationRepository = conversationRepository;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.schoolService = schoolService;
        this.agentProvider = "agent".equalsIgnoreCase(providerName);
    }

    public AiChatResponse chat(String question) {
        AiProviderResult result = agentProvider
                ? answerWithAgent(question)
                : answerWithRag(question).orElseGet(() -> aiProvider.answer(question));
        conversationRepository.save(question, result.answer(), result.relatedSchoolId(), result.sourceSummary());
        return new AiChatResponse(result.answer(), result.sources(), result.meta());
    }

    private AiProviderResult answerWithAgent(String question) {
        try {
            return aiProvider.answer(question);
        } catch (RuntimeException error) {
            log.warn("LangGraph agent unavailable, falling back to local document retrieval: {}", error.getMessage());
            return answerWithRag(question).orElseGet(() -> new AiProviderResult(
                    "智能检索服务暂时不可用，且本地资料未找到可直接回答该问题的证据。请稍后重试。",
                    null,
                    "LangGraph 服务不可用",
                    List.of("系统已记录本次降级，结论未使用推测数据。"),
                    new AiExecutionMeta("spring-fallback", null, "DEGRADED", 0.0, "fallback", 0, List.of())
            ));
        }
    }

    public List<AiConversationDto> conversations(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return conversationRepository.findRecent(safeLimit);
    }

    private Optional<AiProviderResult> answerWithRag(String question) {
        Optional<SchoolSummary> matchedSchool = matchSchool(question);
        Long schoolId = matchedSchool.map(SchoolSummary::id).orElse(null);
        List<DocumentChunkDto> chunks = retrieveChunks(question, schoolId);
        if (chunks.isEmpty()) {
            return Optional.empty();
        }

        Map<Long, SourceDocumentDto> documents = new LinkedHashMap<>();
        for (DocumentChunkDto chunk : chunks) {
            documents.computeIfAbsent(chunk.documentId(), documentRepository::findById);
        }

        String answer = buildRagAnswer(question, matchedSchool.orElse(null), chunks, documents);
        List<String> sources = chunks.stream()
                .map(chunk -> citation(chunk, documents.get(chunk.documentId())))
                .distinct()
                .toList();
        String sourceSummary = String.join("；", sources);
        return Optional.of(new AiProviderResult(
                answer, schoolId, sourceSummary, sources,
                new AiExecutionMeta("spring-local-rag", null, "COMPLETED", 0.55, "local_retrieval", chunks.size(), List.of())
        ));
    }

    private Optional<SchoolSummary> matchSchool(String question) {
        return schoolService.list(null, null, null, null, null, null, null, null, null, null).stream()
                .filter(school -> question.contains(school.name()))
                .findFirst();
    }

    private List<DocumentChunkDto> retrieveChunks(String question, Long schoolId) {
        Map<Long, DocumentChunkDto> unique = new LinkedHashMap<>();
        for (String keyword : extractKeywords(question)) {
            for (DocumentChunkDto chunk : chunkRepository.search(keyword, schoolId, null, null, 5)) {
                unique.putIfAbsent(chunk.id(), chunk);
            }
        }
        if (unique.isEmpty() && schoolId != null) {
            for (DocumentChunkDto chunk : chunkRepository.search(null, schoolId, null, null, 5)) {
                unique.putIfAbsent(chunk.id(), chunk);
            }
        }
        return unique.values().stream().limit(5).toList();
    }

    private List<String> extractKeywords(String question) {
        List<String> keywords = new ArrayList<>();
        addIfContains(question, keywords, "408");
        addIfContains(question, keywords, "招生");
        addIfContains(question, keywords, "人数");
        addIfContains(question, keywords, "名额");
        addIfContains(question, keywords, "复试");
        addIfContains(question, keywords, "分数");
        addIfContains(question, keywords, "录取");
        addIfContains(question, keywords, "最低分");
        addIfContains(question, keywords, "平均分");
        addIfContains(question, keywords, "复试线");
        addIfContains(question, keywords, "差额");
        addIfContains(question, keywords, "权重");
        addIfContains(question, keywords, "专业课");
        addIfContains(question, keywords, "科目");
        addIfContains(question, keywords, "参考书");
        addIfContains(question, keywords, "书目");
        addIfContains(question, keywords, "调剂");
        addIfContains(question, keywords, "学费");
        addIfContains(question, keywords, "学制");
        if (keywords.isEmpty() && question != null && !question.isBlank()) {
            keywords.add(question.trim());
        }
        return keywords;
    }

    private void addIfContains(String question, List<String> keywords, String keyword) {
        if (question != null && question.contains(keyword) && !keywords.contains(keyword)) {
            keywords.add(keyword);
        }
    }

    private String buildRagAnswer(String question, SchoolSummary school, List<DocumentChunkDto> chunks,
                                  Map<Long, SourceDocumentDto> documents) {
        String target = school == null ? "你提到的问题" : school.name();
        StringBuilder answer = new StringBuilder();
        answer.append("根据已发布资料，").append(target).append("可参考以下信息：");
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunkDto chunk = chunks.get(i);
            answer.append("\n").append(i + 1).append(". ")
                    .append(compact(chunk.content()))
                    .append("（来源：").append(citation(chunk, documents.get(chunk.documentId()))).append("）");
        }
        answer.append("\n结论仍需以学校研究生院或学院官网最新公告为准。");
        return answer.toString();
    }

    private String citation(DocumentChunkDto chunk, SourceDocumentDto document) {
        String title = document == null ? "资料文档 " + chunk.documentId() : document.title();
        String year = chunk.year() == null ? "年份未标注" : String.valueOf(chunk.year());
        String type = chunk.documentType() == null || chunk.documentType().isBlank() ? "资料" : chunk.documentType();
        return year + " " + title + " / " + type + " / 第 " + chunk.chunkIndex() + " 段";
    }

    private String compact(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180) + "...";
    }
}
