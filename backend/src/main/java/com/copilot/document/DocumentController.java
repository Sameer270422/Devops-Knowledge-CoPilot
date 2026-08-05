package com.copilot.document;

import com.copilot.auth.CurrentUser;
import com.copilot.common.RateLimiter;
import com.copilot.common.TooManyRequestsException;
import com.copilot.document.dto.DocumentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    // Each upload kicks off async extraction + chunking + embedding (real, billed API
    // calls) — capped per the same "rate limiting on the API" commitment as chat.
    private static final int UPLOAD_MAX_PER_WINDOW = 10;
    private static final long UPLOAD_WINDOW_SECONDS = 3600;

    private final DocumentService documentService;
    private final RateLimiter rateLimiter;

    public DocumentController(DocumentService documentService, RateLimiter rateLimiter) {
        this.documentService = documentService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceType", defaultValue = "OTHER") SourceType sourceType
    ) {
        UUID userId = CurrentUser.get().id();
        if (!rateLimiter.allow("upload:" + userId, UPLOAD_MAX_PER_WINDOW, UPLOAD_WINDOW_SECONDS)) {
            throw new TooManyRequestsException("Too many uploads — please wait a while before uploading more.");
        }
        Document document = documentService.upload(userId, file, sourceType);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentResponse.from(document));
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return documentService.listForUser(CurrentUser.get().id()).stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return DocumentResponse.from(documentService.getOwned(CurrentUser.get().id(), id));
    }

    @GetMapping("/{id}/status")
    public DocumentResponse status(@PathVariable UUID id) {
        return DocumentResponse.from(documentService.getOwned(CurrentUser.get().id(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.deleteOwned(CurrentUser.get().id(), id);
        return ResponseEntity.noContent().build();
    }
}
