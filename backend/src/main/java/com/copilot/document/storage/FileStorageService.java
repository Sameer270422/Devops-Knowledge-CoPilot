package com.copilot.document.storage;

/**
 * Abstraction over "where document bytes live." LocalFileStorageService backs local dev
 * (docker-compose); S3FileStorageService (profile "aws") is what actually runs in
 * production per the Stage 2 architecture. Nothing above this interface knows or cares
 * which one is active.
 */
public interface FileStorageService {

    /** Stores the bytes and returns an opaque storage key — never a public URL. */
    String store(String suggestedKey, byte[] content);

    byte[] load(String storageKey);

    void delete(String storageKey);
}
