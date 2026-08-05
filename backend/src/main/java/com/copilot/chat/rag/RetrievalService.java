package com.copilot.chat.rag;

import com.copilot.llm.EmbeddingClient;
import com.copilot.vector.RetrievedChunk;
import com.copilot.vector.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RetrievalService {

    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;
    private final int topK;

    public RetrievalService(
            EmbeddingClient embeddingClient,
            VectorStore vectorStore,
            @Value("${app.rag.top-k:5}") int topK
    ) {
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.topK = topK;
    }

    /** Ownership scoping happens inside VectorStore's query, not here — this method
     *  physically cannot return another user's chunks no matter what userId is passed. */
    public List<RetrievedChunk> retrieve(UUID userId, String question) {
        float[] queryEmbedding = embeddingClient.embed(question);
        return vectorStore.similaritySearch(userId, queryEmbedding, topK);
    }
}
