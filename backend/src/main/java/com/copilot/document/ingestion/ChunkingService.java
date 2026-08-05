package com.copilot.document.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits extracted text into overlapping chunks along paragraph boundaries where
 * possible (per the Stage 2 RAG design: ~500-800 tokens, ~10-15% overlap). We
 * approximate tokens as ~4 characters each, which is close enough for chunk sizing —
 * it doesn't need to be exact, just consistent.
 */
@Component
public class ChunkingService {

    private static final int TARGET_CHARS = 2800;   // ~700 tokens
    private static final int OVERLAP_CHARS = 350;    // ~12.5%
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n");

    public List<String> chunk(String text) {
        List<String> paragraphs = splitOnParagraphs(text);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() > TARGET_CHARS && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                // Carry the tail of the previous chunk forward so context isn't lost
                // right at the boundary between two chunks. Cap how much tail we carry
                // so it never displaces room for the paragraph about to be appended —
                // without this cap, a paragraph that's itself already near TARGET_CHARS
                // (which happens right after hardSplitIfNeeded slices an oversized
                // paragraph into TARGET_CHARS-sized pieces) plus a full overlap tail
                // would produce a chunk bigger than TARGET_CHARS, breaking the one
                // invariant chunk size is supposed to guarantee. Caught by
                // ChunkingServiceTest#singleParagraphLongerThanTargetIsHardSplit.
                int roomForTail = Math.max(0, TARGET_CHARS - paragraph.length() - 2);
                int tailLen = Math.min(OVERLAP_CHARS, Math.min(roomForTail, current.length()));
                String tail = tailLen > 0 ? current.substring(current.length() - tailLen) : "";
                current = new StringBuilder(tail);
            }
            current.append(paragraph).append("\n\n");
        }
        if (!current.toString().isBlank()) {
            chunks.add(current.toString().trim());
        }
        return chunks.isEmpty() ? List.of(text.trim()) : chunks;
    }

    private List<String> splitOnParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        for (String p : PARAGRAPH_BREAK.split(text)) {
            if (!p.isBlank()) {
                // A single paragraph longer than the whole chunk target still needs
                // splitting on its own, or one giant paragraph would become one giant chunk.
                paragraphs.addAll(hardSplitIfNeeded(p.trim()));
            }
        }
        return paragraphs;
    }

    private List<String> hardSplitIfNeeded(String paragraph) {
        if (paragraph.length() <= TARGET_CHARS) {
            return List.of(paragraph);
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + TARGET_CHARS, paragraph.length());
            parts.add(paragraph.substring(start, end));
            start = end;
        }
        return parts;
    }
}
