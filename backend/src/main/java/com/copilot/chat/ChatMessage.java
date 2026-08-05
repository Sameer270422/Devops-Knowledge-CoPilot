package com.copilot.chat;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    // Hibernate 6's native JSON mapping — serializes straight to/from the jsonb column,
    // no manual converter needed. Empty for USER messages; populated for ASSISTANT
    // messages with the ids of the chunks that were retrieved to answer the question.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cited_chunk_ids", columnDefinition = "jsonb", nullable = false)
    private List<UUID> citedChunkIds = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ChatMessage(UUID sessionId, MessageRole role, String content, List<UUID> citedChunkIds) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.citedChunkIds = citedChunkIds != null ? citedChunkIds : new ArrayList<>();
    }
}
