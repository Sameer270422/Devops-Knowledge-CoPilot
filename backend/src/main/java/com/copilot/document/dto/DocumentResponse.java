package com.copilot.document.dto;

import com.copilot.document.Document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id, String filename, String sourceType, String status, String errorMessage, Instant uploadedAt
) {
    public static DocumentResponse from(Document d) {
        return new DocumentResponse(
                d.getId(), d.getFilename(), d.getSourceType().name(),
                d.getStatus().name(), d.getErrorMessage(), d.getUploadedAt());
    }
}
