package com.copilot.document.ingestion;

import com.copilot.document.Chunk;
import com.copilot.document.ChunkRepository;
import com.copilot.document.Document;
import com.copilot.document.DocumentRepository;
import com.copilot.document.DocumentStatus;
import com.copilot.document.storage.FileStorageService;
import com.copilot.llm.EmbeddingClient;
import com.copilot.vector.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * The Document service's async pipeline: extract -> chunk -> embed -> store. Runs off the
 * request thread (see AsyncConfig) so upload returns immediately and the client polls
 * document status instead of blocking on what can be a slow embedding call.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final FileStorageService fileStorageService;
    private final TextExtractor textExtractor;
    private final ChunkingService chunkingService;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;

    public IngestionService(
            DocumentRepository documentRepository,
            ChunkRepository chunkRepository,
            FileStorageService fileStorageService,
            TextExtractor textExtractor,
            ChunkingService chunkingService,
            EmbeddingClient embeddingClient,
            VectorStore vectorStore
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.fileStorageService = fileStorageService;
        this.textExtractor = textExtractor;
        this.chunkingService = chunkingService;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    @Async("ingestionExecutor")
    public void ingestAsync(UUID documentId) {
        try {
            processDocument(documentId);
        } catch (Exception e) {
            log.error("Ingestion failed for document {}", documentId, e);
            markFailed(documentId, e.getMessage());
        }
    }

    private void processDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Document disappeared mid-ingestion: " + documentId));

        byte[] fileBytes = fileStorageService.load(document.getStorageKey());
        String text = textExtractor.extract(document.getFilename(), fileBytes);

        if (text.isBlank()) {
            throw new IllegalArgumentException("No extractable text found in document");
        }

        List<String> chunkTexts = chunkingService.chunk(text);
        List<float[]> embeddings = embeddingClient.embedBatch(chunkTexts);

        for (int i = 0; i < chunkTexts.size(); i++) {
            Chunk chunk = new Chunk(document.getId(), document.getUserId(), i, chunkTexts.get(i), document.getSourceType());
            chunk = chunkRepository.save(chunk);
            vectorStore.saveEmbedding(chunk.getId(), embeddings.get(i));
        }

        document.setStatus(DocumentStatus.INDEXED);
        document.setErrorMessage(null);
        documentRepository.save(document);
        log.info("Indexed document {} into {} chunks", documentId, chunkTexts.size());
    }

    // Not @Transactional: this is called from ingestAsync() on the same instance, and
    // self-invocation bypasses Spring's proxy-based AOP, so an annotation here would be
    // silently ignored anyway. documentRepository.save() below is transactional on its
    // own (Spring Data repositories wrap each method individually), which is all a
    // single-entity update actually needs.
    private void markFailed(UUID documentId, String reason) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(reason != null ? reason : "Unknown ingestion error");
            documentRepository.save(doc);
        });
    }
}
