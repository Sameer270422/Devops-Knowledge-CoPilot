package com.copilot.vector;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs against a real, disposable Postgres+pgvector instance (via Testcontainers) rather
 * than mocks - VectorStore speaks raw pgvector SQL (embedding <=> ?::vector), which a
 * mocked JdbcTemplate couldn't meaningfully verify. Flyway applies the real V1__init.sql
 * migration against this container on context startup, same as production.
 */
@Testcontainers
@SpringBootTest
class VectorStoreIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("copilot_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Not exercised by these tests, but the full application context still needs
        // valid-enough values to start (JwtService validates its secret length at
        // construction time; the LLM/embedding clients just store the key, no live call).
        registry.add("app.jwt.secret", () -> "test-only-secret-do-not-use-in-prod-1234567890");
        registry.add("app.llm.api-key", () -> "unused-in-this-test");
        registry.add("app.embedding.api-key", () -> "unused-in-this-test");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VectorStore vectorStore;

    @Test
    void similaritySearchReturnsTheClosestVectorFirst() {
        UUID userId = insertTestUser("user1@example.com");
        UUID documentId = insertTestDocument(userId, "runbook.md");
        UUID chunkA = insertTestChunk(documentId, userId, 0, "content about kubernetes pods");
        UUID chunkB = insertTestChunk(documentId, userId, 1, "content about database backups");

        // Simple 3-dim vectors for a deterministic, easy-to-reason-about test - the real
        // app uses 1536-dim OpenAI embeddings, but cosine-distance ordering works
        // identically regardless of dimensionality.
        vectorStore.saveEmbedding(chunkA, new float[]{1f, 0f, 0f});
        vectorStore.saveEmbedding(chunkB, new float[]{0f, 1f, 0f});

        List<RetrievedChunk> results = vectorStore.similaritySearch(userId, new float[]{0.9f, 0.1f, 0f}, 5);

        assertFalse(results.isEmpty());
        assertEquals(chunkA, results.get(0).chunkId(), "expected the closer vector to rank first");
    }

    @Test
    void oneUsersChunksAreNeverReturnedForAnotherUsersSearch() {
        // The concrete test of the security plan's core claim: "one tenant can never
        // retrieve another's documents." This is enforced at the SQL level in VectorStore
        // (WHERE c.user_id = ?) - this test fails loudly if that clause is ever
        // accidentally removed or weakened in a future refactor.
        UUID userA = insertTestUser("user-a@example.com");
        UUID userB = insertTestUser("user-b@example.com");

        UUID docA = insertTestDocument(userA, "user-a-private-runbook.md");
        UUID chunkA = insertTestChunk(docA, userA, 0, "user A's private incident details");
        vectorStore.saveEmbedding(chunkA, new float[]{1f, 0f, 0f});

        // User B searches with the exact same vector user A's chunk was stored under.
        List<RetrievedChunk> resultsForUserB = vectorStore.similaritySearch(userB, new float[]{1f, 0f, 0f}, 5);

        assertTrue(resultsForUserB.isEmpty(),
                "user B must not be able to retrieve user A's chunks, even with an identical query vector");
    }

    private UUID insertTestUser(String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, role) VALUES (?, ?, ?, 'USER')",
                id, email, "not-a-real-hash");
        return id;
    }

    private UUID insertTestDocument(UUID userId, String filename) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO documents (id, user_id, filename, source_type, storage_key, status) " +
                        "VALUES (?, ?, ?, 'OTHER', ?, 'INDEXED')",
                id, userId, filename, "test/" + filename);
        return id;
    }

    private UUID insertTestChunk(UUID documentId, UUID userId, int index, String content) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO chunks (id, document_id, user_id, chunk_index, content, source_type) " +
                        "VALUES (?, ?, ?, ?, ?, 'OTHER')",
                id, documentId, userId, index, content);
        return id;
    }
}
