package com.copilot.llm;

import java.util.List;

/** Turns text into a 1536-dim vector for storage/search in pgvector. */
public interface EmbeddingClient {
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
}
