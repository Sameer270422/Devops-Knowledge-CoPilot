package com.copilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// Spins up its own throwaway pgvector container (same pattern as
// VectorStoreIntegrationTest) instead of relying on a Postgres you have to remember to
// start by hand first. Locally that used to mean this test failed with a bare
// PSQLException the moment nobody had run `docker compose up -d postgres` beforehand,
// which is exactly the kind of environment-dependent flakiness a smoke test shouldn't have.
//
// "test" profile pulls in application-test.yml, which points LocalFileStorageService at
// a writable build-directory path instead of the production default (/data/documents) —
// without it, booting the full context outside Docker fails on AccessDeniedException
// trying to mkdir a root-level path.
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class CopilotApplicationTests {

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
        registry.add("app.jwt.secret", () -> "test-only-secret-do-not-use-in-prod-1234567890");
        registry.add("app.llm.api-key", () -> "unused-in-this-test");
        registry.add("app.embedding.api-key", () -> "unused-in-this-test");
    }

    @Test
    void contextLoads() {
        // Smoke test: fails the build if the Spring context can't start.
        // Real service/controller tests are added module-by-module in Stage 4.
    }
}
