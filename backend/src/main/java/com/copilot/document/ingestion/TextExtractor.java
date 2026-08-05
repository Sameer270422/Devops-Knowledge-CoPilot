package com.copilot.document.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Pulls plain text out of an uploaded file so it can be chunked. Extend here as more
 *  formats are supported — the ingestion pipeline downstream doesn't care about format. */
@Component
public class TextExtractor {

    public String extract(String filename, byte[] content) {
        String lower = filename.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".pdf")) {
                return extractPdf(content);
            }
            // .txt, .md, .log, .csv, and anything else we treat as plain text.
            return new String(content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not extract text from " + filename + ": " + e.getMessage(), e);
        }
    }

    private String extractPdf(byte[] content) throws IOException {
        try (PDDocument doc = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(doc);
        }
    }
}
