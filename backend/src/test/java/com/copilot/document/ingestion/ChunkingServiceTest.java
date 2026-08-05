package com.copilot.document.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkingServiceTest {

    private final ChunkingService chunkingService = new ChunkingService();

    @Test
    void shortTextProducesExactlyOneChunk() {
        String text = "A short runbook paragraph that fits well within one chunk.";
        List<String> chunks = chunkingService.chunk(text);
        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0));
    }

    @Test
    void emptyOrBlankTextStillReturnsOneChunkRatherThanNone() {
        // IngestionService already rejects blank text before calling the chunker, but the
        // chunker itself should never silently return zero chunks for non-null input -
        // that would look like "successfully indexed zero pieces" instead of failing loudly.
        List<String> chunks = chunkingService.chunk("   ");
        assertEquals(1, chunks.size());
    }

    @Test
    void longDocumentIsSplitIntoMultipleChunks() {
        // ~15 paragraphs of ~300 chars each - well past the ~2800 char target, so this
        // must produce more than one chunk.
        StringBuilder sb = new StringBuilder();
        String paragraph = "This is a sentence about deploying services to Kubernetes. ".repeat(6);
        for (int i = 0; i < 15; i++) {
            sb.append(paragraph).append("\n\n");
        }
        List<String> chunks = chunkingService.chunk(sb.toString());
        assertTrue(chunks.size() > 1, "expected multiple chunks for a long document, got " + chunks.size());
    }

    @Test
    void consecutiveChunksOverlapSoContextIsntLostAtTheBoundary() {
        StringBuilder sb = new StringBuilder();
        String paragraph = "Sentence about incident response procedures and escalation paths. ".repeat(6);
        for (int i = 0; i < 15; i++) {
            sb.append(paragraph).append("\n\n");
        }
        List<String> chunks = chunkingService.chunk(sb.toString());
        assertTrue(chunks.size() > 1);

        // The tail of chunk N should reappear near the start of chunk N+1 - that's the
        // overlap the chunker is supposed to preserve so context isn't lost at a chunk
        // boundary. Use a short, whitespace-safe window rather than an exact large-window
        // match, since trimming can shift exactly where a large window's edges fall.
        String chunk0 = chunks.get(0).trim();
        String shortTail = chunk0.substring(Math.max(0, chunk0.length() - 20));
        assertTrue(chunks.get(1).contains(shortTail),
                "expected chunk 2 to contain the tail of chunk 1 (overlap): [" + shortTail + "]");
    }

    @Test
    void singleParagraphLongerThanTargetIsHardSplit() {
        // One giant paragraph (no blank-line breaks at all) longer than the chunk target
        // must still get split, not become one oversized chunk.
        String oneHugeParagraph = "x".repeat(6000);
        List<String> chunks = chunkingService.chunk(oneHugeParagraph);
        assertTrue(chunks.size() > 1);
        for (String c : chunks) {
            assertTrue(c.length() <= 2800, "chunk exceeded target size: " + c.length());
        }
    }
}
