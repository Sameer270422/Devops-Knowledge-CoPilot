package com.copilot.vector;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The one place in the app that speaks pgvector's SQL directly. Spring Data JPA doesn't
 * have a first-class mapping for the `vector` column type, so embeddings are written and
 * queried here via plain JdbcTemplate rather than through the Chunk entity (see Chunk.java).
 *
 * Every query is scoped by user_id — this is the actual enforcement point for "one user's
 * documents are never retrievable by another user" from the security plan. When Phase 2
 * (multi-tenant) lands, user_id here becomes workspace_id; the shape of these queries
 * doesn't otherwise change.
 */
@Component
public class VectorStore {

    private final JdbcTemplate jdbcTemplate;

    public VectorStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveEmbedding(UUID chunkId, float[] embedding) {
        jdbcTemplate.update(
                "UPDATE chunks SET embedding = ?::vector WHERE id = ?",
                toVectorLiteral(embedding), chunkId);
    }

    public List<RetrievedChunk> similaritySearch(UUID userId, float[] queryEmbedding, int topK) {
        String sql = """
            SELECT c.id AS chunk_id, c.document_id, d.filename, c.content, c.source_type,
                   c.embedding <=> ?::vector AS distance
            FROM chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE c.user_id = ? AND c.embedding IS NOT NULL
            ORDER BY c.embedding <=> ?::vector
            LIMIT ?
            """;
        String literal = toVectorLiteral(queryEmbedding);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RetrievedChunk(
                UUID.fromString(rs.getString("chunk_id")),
                UUID.fromString(rs.getString("document_id")),
                rs.getString("filename"),
                rs.getString("content"),
                rs.getString("source_type"),
                rs.getDouble("distance")
        ), literal, userId, literal, topK);
    }

    private String toVectorLiteral(float[] embedding) {
        return "[" + IntStream.range(0, embedding.length)
                .mapToObj(i -> Float.toString(embedding[i]))
                .collect(Collectors.joining(",")) + "]";
    }
}
