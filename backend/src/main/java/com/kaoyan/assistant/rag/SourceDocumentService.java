package com.kaoyan.assistant.rag;

import com.kaoyan.assistant.quality.EvidenceChangedEvent;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class SourceDocumentService {

    private static final int CHUNK_SIZE = 600;
    private static final int CHUNK_OVERLAP = 80;
    private static final long MAX_PARSE_FILE_SIZE = 20L * 1024 * 1024;
    private static final String PARSER_VERSION = "source-parser-v1";

    private final SourceDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final SourceDocumentVersionRepository versionRepository;
    private final DocumentParseTaskRepository parseTaskRepository;
    private final OcrEngine ocrEngine;
    private final ApplicationEventPublisher eventPublisher;

    public SourceDocumentService(SourceDocumentRepository documentRepository, DocumentChunkRepository chunkRepository,
                                 SourceDocumentVersionRepository versionRepository,
                                 DocumentParseTaskRepository parseTaskRepository,
                                 OcrEngine ocrEngine,
                                 ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.versionRepository = versionRepository;
        this.parseTaskRepository = parseTaskRepository;
        this.ocrEngine = ocrEngine;
        this.eventPublisher = eventPublisher;
    }

    public List<SourceDocumentDto> list(Long schoolId, String auditStatus) {
        return documentRepository.findAll(schoolId, auditStatus);
    }

    public SourceDocumentDto detail(Long id) {
        return documentRepository.findById(id);
    }

    @Transactional
    public SourceDocumentDto create(SourceDocumentRequest request) {
        return create(request, "system");
    }

    @Transactional
    public SourceDocumentDto create(SourceDocumentRequest request, String operator) {
        Long id = documentRepository.create(request);
        SourceDocumentDto result = documentRepository.findById(id);
        versionRepository.snapshot(result, "CREATE", operator);
        publishChange(result == null ? null : result.schoolId());
        return result;
    }

    @Transactional
    public SourceDocumentBatchImportResult batchImport(List<SourceDocumentRequest> requests, boolean generateChunks) {
        return batchImport(requests, generateChunks, "system");
    }

    @Transactional
    public SourceDocumentBatchImportResult batchImport(List<SourceDocumentRequest> requests, boolean generateChunks,
                                                       String operator) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("documents is empty");
        }
        if (requests.size() > 100) {
            throw new IllegalArgumentException("batch import supports at most 100 documents");
        }
        SourceDocumentQualityReport report = qualityCheck(requests);
        if (!report.importable()) {
            throw new IllegalArgumentException("batch import quality check failed");
        }
        List<Long> ids = new ArrayList<>();
        List<Long> changedSchoolIds = new ArrayList<>();
        int chunkCount = 0;
        for (SourceDocumentRequest request : requests) {
            if (request.title() == null || request.title().isBlank()) {
                throw new IllegalArgumentException("document title is required");
            }
            if (request.rawText() == null || request.rawText().isBlank()) {
                throw new IllegalArgumentException("document rawText is required");
            }
            Long id = documentRepository.create(request);
            ids.add(id);
            versionRepository.snapshot(documentRepository.findById(id), "CREATE", operator);
            if (request.schoolId() != null && !changedSchoolIds.contains(request.schoolId())) {
                changedSchoolIds.add(request.schoolId());
            }
            if (generateChunks) {
                chunkCount += generateChunks(id).size();
            }
        }
        changedSchoolIds.forEach(this::publishChange);
        return new SourceDocumentBatchImportResult(ids.size(), chunkCount, ids);
    }

    public SourceDocumentQualityReport qualityCheck(List<SourceDocumentRequest> requests) {
        List<SourceDocumentQualityIssue> issues = new ArrayList<>();
        if (requests == null || requests.isEmpty()) {
            issues.add(new SourceDocumentQualityIssue(0, "ERROR", "documents", "documents is empty"));
            return report(0, issues);
        }
        if (requests.size() > 100) {
            issues.add(new SourceDocumentQualityIssue(0, "ERROR", "documents", "at most 100 documents per batch"));
        }
        for (int i = 0; i < requests.size(); i++) {
            validateDocument(i, requests.get(i), issues);
        }
        return report(requests.size(), issues);
    }

    @Transactional
    public SourceDocumentDto update(Long id, SourceDocumentRequest request) {
        return update(id, request, "system");
    }

    @Transactional
    public SourceDocumentDto update(Long id, SourceDocumentRequest request, String operator) {
        SourceDocumentDto previous = documentRepository.findById(id);
        if (previous == null) {
            throw new IllegalArgumentException("source document not found");
        }
        ensureBaseline(previous, operator);
        documentRepository.update(id, request);
        SourceDocumentDto result = documentRepository.findById(id);
        versionRepository.snapshot(result, "UPDATE", operator);
        publishChange(previous == null ? null : previous.schoolId());
        publishChange(result == null ? null : result.schoolId());
        return result;
    }

    public List<SourceDocumentVersionDto> versions(Long documentId) {
        if (documentRepository.findById(documentId) == null) {
            throw new IllegalArgumentException("source document not found");
        }
        return versionRepository.findAll(documentId);
    }

    @Transactional
    public SourceDocumentRollbackResult rollback(Long documentId, Integer versionNo, String operator) {
        SourceDocumentDto current = documentRepository.findById(documentId);
        if (current == null) {
            throw new IllegalArgumentException("source document not found");
        }
        SourceDocumentVersionDto target = versionRepository.find(documentId, versionNo);
        if (target == null) {
            throw new IllegalArgumentException("source document version not found");
        }
        ensureBaseline(current, operator);
        documentRepository.update(documentId, target.toRequest());
        SourceDocumentDto restored = documentRepository.findById(documentId);
        List<DocumentChunkDto> chunks = generateChunks(documentId);
        SourceDocumentVersionDto rollbackVersion = versionRepository.snapshot(restored, "ROLLBACK", operator);
        publishChange(current.schoolId());
        publishChange(restored.schoolId());
        return new SourceDocumentRollbackResult(
                restored, versionNo, rollbackVersion.versionNo(), chunks.size()
        );
    }

    @Transactional
    public void delete(Long id) {
        SourceDocumentDto previous = documentRepository.findById(id);
        versionRepository.deleteByDocumentId(id);
        documentRepository.delete(id);
        publishChange(previous == null ? null : previous.schoolId());
    }

    public List<DocumentChunkDto> generateChunks(Long documentId) {
        SourceDocumentDto document = documentRepository.findById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("source document not found");
        }
        chunkRepository.deleteByDocumentId(documentId);
        List<String> chunks = splitText(document.rawText());
        for (int i = 0; i < chunks.size(); i++) {
            chunkRepository.create(new DocumentChunkDto(
                    null,
                    document.id(),
                    document.schoolId(),
                    document.collegeId(),
                    document.majorId(),
                    document.year(),
                    document.documentType(),
                    i + 1,
                    chunks.get(i),
                    null,
                    document.auditStatus(),
                    null
            ));
        }
        return chunkRepository.findByDocumentId(documentId);
    }

    public List<DocumentChunkDto> chunks(Long documentId) {
        return chunkRepository.findByDocumentId(documentId);
    }

    public List<DocumentChunkDto> searchChunks(String keyword, Long schoolId, Integer year, String documentType, int limit) {
        return chunkRepository.search(keyword, schoolId, year, documentType, limit);
    }

    private void validateDocument(int index, SourceDocumentRequest request, List<SourceDocumentQualityIssue> issues) {
        if (request == null) {
            issues.add(new SourceDocumentQualityIssue(index, "ERROR", "document", "document is null"));
            return;
        }
        if (request.title() == null || request.title().isBlank()) {
            issues.add(new SourceDocumentQualityIssue(index, "ERROR", "title", "title is required"));
        }
        if (request.rawText() == null || request.rawText().isBlank()) {
            issues.add(new SourceDocumentQualityIssue(index, "ERROR", "rawText", "rawText is required"));
        } else if (request.rawText().trim().length() < 30) {
            issues.add(new SourceDocumentQualityIssue(index, "WARNING", "rawText", "rawText is too short for reliable RAG"));
        }
        if (request.schoolId() == null) {
            issues.add(new SourceDocumentQualityIssue(index, "WARNING", "schoolId", "schoolId is recommended"));
        }
        if (request.year() == null) {
            issues.add(new SourceDocumentQualityIssue(index, "WARNING", "year", "year is recommended"));
        }
        if (request.sourceUrl() == null || request.sourceUrl().isBlank()) {
            issues.add(new SourceDocumentQualityIssue(index, "WARNING", "sourceUrl", "official sourceUrl is recommended"));
        }
        if ("PUBLISHED".equalsIgnoreCase(request.auditStatus())) {
            if (!"OFFICIAL".equalsIgnoreCase(request.sourceReliability())) {
                issues.add(new SourceDocumentQualityIssue(index, "WARNING", "sourceReliability", "published documents should be OFFICIAL when possible"));
            }
            if (request.sourceUrl() == null || request.sourceUrl().isBlank()) {
                issues.add(new SourceDocumentQualityIssue(index, "ERROR", "sourceUrl", "published documents require sourceUrl"));
            }
        }
        if (request.title() != null && !request.title().isBlank() && documentRepository.duplicateCount(request) > 0) {
            issues.add(new SourceDocumentQualityIssue(index, "WARNING", "duplicate", "possible duplicate by sourceUrl or title/school/year"));
        }
    }

    private SourceDocumentQualityReport report(int totalCount, List<SourceDocumentQualityIssue> issues) {
        int errorCount = (int) issues.stream().filter(issue -> "ERROR".equals(issue.level())).count();
        int warningCount = (int) issues.stream().filter(issue -> "WARNING".equals(issue.level())).count();
        boolean importable = errorCount == 0;
        boolean publishable = importable && warningCount == 0;
        return new SourceDocumentQualityReport(totalCount, errorCount, warningCount, importable, publishable, issues);
    }

    public ParsedSourceDocumentDraft parseTextFile(MultipartFile file, String documentType) {
        return parseTextFile(file, documentType, "system");
    }

    public ParsedSourceDocumentDraft parseTextFile(MultipartFile file, String documentType, String operator) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }
        if (file.getSize() > MAX_PARSE_FILE_SIZE) {
            throw new IllegalArgumentException("file is too large; maximum size is 20 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("failed to read file");
        }
        String filename = file.getOriginalFilename() == null ? "未命名资料" : file.getOriginalFilename();
        String lowerName = filename.toLowerCase();
        boolean pdf = lowerName.endsWith(".pdf");
        String sha256 = sha256(bytes);
        DocumentParseTaskRepository.DocumentParseTaskRecord existing = parseTaskRepository.findByHash(sha256);
        if (existing != null && "COMPLETED".equals(existing.status())) {
            return toParsedDraft(parseTaskRepository.markReused(sha256), true);
        }
        String parserType = pdf ? "PDF_TEXT" : "UTF8_TEXT";
        DocumentParseTaskRepository.ParseTaskInput taskInput = new DocumentParseTaskRepository.ParseTaskInput(
                sha256, filename, file.getContentType(), bytes.length, parserType, PARSER_VERSION,
                operator == null || operator.isBlank() ? "system" : operator
        );
        try {
            if (!(pdf || lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".csv"))) {
                throw new IllegalArgumentException("only pdf, txt, md and csv files are supported");
            }
            String rawText = pdf ? extractPdfText(bytes) : new String(bytes, StandardCharsets.UTF_8);
            if (pdf && rawText.isBlank()) {
                if (!ocrEngine.isAvailable()) {
                    throw new IllegalArgumentException("PDF contains no text layer; OCR engine is unavailable");
                }
                OcrEngine.OcrResult ocr = ocrEngine.recognizePdf(bytes);
                rawText = ocr.text();
                parserType = "PDF_OCR";
                taskInput = new DocumentParseTaskRepository.ParseTaskInput(
                        sha256, filename, file.getContentType(), bytes.length, parserType, ocr.engineVersion(),
                        operator == null || operator.isBlank() ? "system" : operator
                );
            }
            rawText = normalizeImportedText(rawText);
            if (rawText.isBlank()) {
                throw new IllegalArgumentException(pdf
                        ? "PDF contains no extractable text; scanned documents require OCR"
                        : "file content is empty");
            }
            String title = stripExtension(filename);
            String type = documentType == null || documentType.isBlank()
                    ? inferDocumentType(filename, rawText) : documentType.trim();
            String remark = "PDF_OCR".equals(parserType)
                    ? "由扫描 PDF OCR 生成草稿，需管理员逐页核对原文后发布"
                    : pdf
                    ? "由 PDF 文本层解析生成草稿，需管理员核对原文后发布"
                    : "由文件导入生成草稿，需管理员确认后发布";
            DocumentParseTaskRepository.DocumentParseTaskRecord task = parseTaskRepository.saveSuccess(
                    taskInput, new DocumentParseTaskRepository.ParsedContent(title, type, rawText, remark)
            );
            return toParsedDraft(task, false);
        } catch (IOException ex) {
            String message = pdf ? "failed to parse PDF file" : "failed to read file";
            parseTaskRepository.saveFailure(taskInput, message);
            throw new IllegalArgumentException(message);
        } catch (IllegalArgumentException ex) {
            parseTaskRepository.saveFailure(taskInput, ex.getMessage());
            throw ex;
        }
    }

    public List<DocumentParseTaskDto> parseTasks(int limit) {
        return parseTaskRepository.findRecent(limit);
    }

    private ParsedSourceDocumentDraft toParsedDraft(
            DocumentParseTaskRepository.DocumentParseTaskRecord task, boolean duplicate
    ) {
        return new ParsedSourceDocumentDraft(
                task.title(), task.documentType(), task.rawText(), task.remark(), task.id(),
                task.sha256(), duplicate, task.parserVersion()
        );
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String extractPdfText(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String normalizeImportedText(String text) {
        return text.replace("\uFEFF", "")
                .replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private List<String> splitText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        String normalized = rawText.replace("\r\n", "\n").trim();
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : normalized.split("\\n\\s*\\n|\\n")) {
            String text = paragraph.trim();
            if (!text.isBlank()) {
                paragraphs.add(text);
            }
        }
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.length() > CHUNK_SIZE) {
                flushChunk(current, result);
                splitLongParagraph(paragraph, result);
                continue;
            }
            if (current.length() + paragraph.length() + 1 > CHUNK_SIZE && !current.isEmpty()) {
                flushChunk(current, result);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(paragraph);
        }
        flushChunk(current, result);
        return result;
    }

    private void splitLongParagraph(String paragraph, List<String> result) {
        int step = CHUNK_SIZE - CHUNK_OVERLAP;
        for (int start = 0; start < paragraph.length(); start += step) {
            int end = Math.min(start + CHUNK_SIZE, paragraph.length());
            String chunk = paragraph.substring(start, end).trim();
            if (!chunk.isBlank()) {
                result.add(chunk);
            }
            if (end == paragraph.length()) {
                break;
            }
        }
    }

    private void flushChunk(StringBuilder current, List<String> result) {
        if (!current.isEmpty()) {
            result.add(current.toString());
            current.setLength(0);
        }
    }

    private String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index > 0 ? filename.substring(0, index) : filename;
    }

    private String inferDocumentType(String filename, String rawText) {
        String text = filename + "\n" + rawText;
        if (text.contains("复试")) {
            return "复试细则";
        }
        if (text.contains("招生简章")) {
            return "招生简章";
        }
        if (text.contains("专业目录") || text.contains("科目")) {
            return "招生专业目录";
        }
        return "资料文档";
    }

    private void publishChange(Long schoolId) {
        if (schoolId != null) {
            eventPublisher.publishEvent(new EvidenceChangedEvent(schoolId));
        }
    }

    private void ensureBaseline(SourceDocumentDto document, String operator) {
        if (!versionRepository.hasVersions(document.id())) {
            versionRepository.snapshot(document, "BASELINE", operator);
        }
    }
}
