package com.copilot.document;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The `embedding vector(1536)` column deliberately has no JPA-mapped field here — pgvector's
 * type isn't a first-class Hibernate type without extra tooling, and hand-rolling a
 * UserType for one column isn't worth it. Inserts/reads of the embedding go through
 * VectorStore via plain JDBC instead (see com.copilot.vector.VectorStore).
 */
@Entity
@Table(name = "chunks")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chunk {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Chunk(UUID documentId, UUID userId, int chunkIndex, String content, SourceType sourceType) {
        this.documentId = documentId;
        this.userId = userId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.sourceType = sourceType;
    }
}
