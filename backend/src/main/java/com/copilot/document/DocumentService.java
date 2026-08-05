package com.copilot.document;

import com.copilot.common.ResourceNotFoundException;
import com.copilot.document.ingestion.IngestionService;
import com.copilot.document.storage.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final FileStorageService fileStorageService;
    private final DocumentUploadValidator validator;
    private final IngestionService ingestionService;

    public DocumentService(
            DocumentRepository documentRepository,
            ChunkRepository chunkRepository,
            FileStorageService fileStorageService,
            DocumentUploadValidator validator,
            IngestionService ingestionService
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.fileStorageService = fileStorageService;
        this.validator = validator;
        this.ingestionService = ingestionService;
    }

    @Transactional
    public Document upload(UUID userId, MultipartFile file, SourceType sourceType) {
        validator.validate(file);

        String storageKey = userId + "/" + UUID.randomUUID() + "-" + sanitizeFilename(file.getOriginalFilename());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        fileStorageService.store(storageKey, content);

        Document document = new Document(userId, file.getOriginalFilename(), sourceType, storageKey);
        document = documentRepository.save(document);

        // Fire-and-forget: the async method runs on IngestionService's own proxy, so this
        // call returns immediately and the transaction above commits independently.
        ingestionService.ingestAsync(document.getId());

        return document;
    }

    public List<Document> listForUser(UUID userId) {
        return documentRepository.findByUserIdOrderByUploadedAtDesc(userId);
    }

    public Document getOwned(UUID userId, UUID documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    @Transactional
    public void deleteOwned(UUID userId, UUID documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        chunkRepository.deleteAll(chunkRepository.findByDocumentIdOrderByChunkIndex(document.getId()));
        fileStorageService.delete(document.getStorageKey());
        documentRepository.delete(document);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "unnamed";
        // Strip anything that isn't a safe filename character before it becomes part of
        // a storage path — same path-traversal concern as LocalFileStorageService checks.
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
