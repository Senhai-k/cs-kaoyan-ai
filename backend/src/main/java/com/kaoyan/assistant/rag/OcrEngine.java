package com.kaoyan.assistant.rag;

public interface OcrEngine {
    boolean isAvailable();
    OcrResult recognizePdf(byte[] pdfBytes);

    record OcrResult(String text, String engineVersion) {
    }
}
