package com.copilot.chat.dto;

import com.copilot.chat.ChatMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id, String role, String content, List<CitationResponse> citations, Instant createdAt
) {
    public static ChatMessageResponse from(ChatMessage m, List<CitationResponse> citations) {
        return new ChatMessageResponse(m.getId(), m.getRole().name(), m.getContent(), citations, m.getCreatedAt());
    }
}
