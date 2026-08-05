package com.copilot.chat.dto;

import java.util.UUID;

public record CitationResponse(UUID documentId, String filename, String excerpt) {
}
