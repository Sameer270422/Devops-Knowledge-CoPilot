package com.copilot.vector;

import java.util.UUID;

public record RetrievedChunk(
        UUID chunkId, UUID documentId, String documentFilename, String content, String sourceType, double distance
) {
}
