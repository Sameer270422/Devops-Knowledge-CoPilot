package com.copilot.chat.dto;

import com.copilot.chat.ChatSession;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(UUID id, String title, Instant createdAt) {
    public static ChatSessionResponse from(ChatSession s) {
        return new ChatSessionResponse(s.getId(), s.getTitle(), s.getCreatedAt());
    }
}
