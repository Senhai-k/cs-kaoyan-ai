package com.kaoyan.assistant.rag;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

@Component
public class TesseractOcrEngine implements OcrEngine {

    private final boolean enabled;
    private final String command;
    private final String language;
    private final int maxPages;
    private final int pageTimeoutSeconds;

    public TesseractOcrEngine(
            @Value("${app.document.ocr.enabled:false}") boolean enabled,
            @Value("${app.document.ocr.command:tesseract}") String command,
            @Value("${app.document.ocr.language:chi_sim+eng}") String language,
            @Value("${app.document.ocr.max-pages:20}") int maxPages,
            @Value("${app.document.ocr.page-timeout-seconds:30}") int pageTimeoutSeconds
    ) {
        this.enabled = enabled;
        this.command = command;
        this.language = language;
        this.maxPages = Math.max(1, Math.min(maxPages, 50));
        this.pageTimeoutSeconds = Math.max(5, Math.min(pageTimeoutSeconds, 120));
    }

    @Override
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        try {
            Process process = new ProcessBuilder(command, "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
            }
            return completed && process.exitValue() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public OcrResult recognizePdf(byte[] pdfBytes) {
        if (!isAvailable()) {
            throw new IllegalArgumentException("OCR engine is disabled or unavailable");
        }
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("cs-kaoyan-ocr-");
            StringBuilder text = new StringBuilder();
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                if (document.getNumberOfPages() > maxPages) {
                    throw new IllegalArgumentException("scanned PDF exceeds OCR page limit " + maxPages);
                }
                PDFRenderer renderer = new PDFRenderer(document);
                for (int page = 0; page < document.getNumberOfPages(); page++) {
                    Path image = workspace.resolve("page-" + (page + 1) + ".png");
                    Path outputBase = workspace.resolve("ocr-" + (page + 1));
                    Path log = workspace.resolve("ocr-" + (page + 1) + ".log");
                    ImageIO.write(renderer.renderImageWithDPI(page, 220), "png", image.toFile());
                    Process process = new ProcessBuilder(
                            command, image.toString(), outputBase.toString(),
                            "-l", language, "--psm", "6"
                    ).redirectError(log.toFile()).start();
                    if (!process.waitFor(pageTimeoutSeconds, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        throw new IllegalArgumentException("OCR page timed out at page " + (page + 1));
                    }
                    if (process.exitValue() != 0) {
                        String detail = Files.exists(log) ? Files.readString(log, StandardCharsets.UTF_8).trim() : "";
                        throw new IllegalArgumentException("OCR failed at page " + (page + 1)
                                + (detail.isBlank() ? "" : ": " + detail));
                    }
                    Path output = Path.of(outputBase + ".txt");
                    if (Files.exists(output)) {
                        text.append(Files.readString(output, StandardCharsets.UTF_8)).append('\n');
                    }
                }
            }
            String result = text.toString().trim();
            if (result.isBlank()) {
                throw new IllegalArgumentException("OCR produced no text");
            }
            return new OcrResult(result, "tesseract-cli-v1:" + language);
        } catch (IOException ex) {
            throw new IllegalArgumentException("failed to process scanned PDF with OCR");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("OCR processing was interrupted");
        } finally {
            deleteWorkspace(workspace);
        }
    }

    private void deleteWorkspace(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
