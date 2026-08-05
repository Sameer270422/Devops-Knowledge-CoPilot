package com.copilot.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByUserIdOrderByUploadedAtDesc(UUID userId);

    // Ownership is enforced at the query level, not just checked after fetching — a
    // document belonging to another user simply doesn't exist as far as this query cares.
    Optional<Document> findByIdAndUserId(UUID id, UUID userId);
}
