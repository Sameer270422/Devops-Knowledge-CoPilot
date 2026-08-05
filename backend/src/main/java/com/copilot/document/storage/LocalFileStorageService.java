package com.copilot.document.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Default storage backend for local dev / docker-compose. Not used in the AWS deployment. */
@Service
@Profile("!aws")
public class LocalFileStorageService implements FileStorageService {

    private final Path baseDir;

    public LocalFileStorageService(@Value("${app.storage.local-dir:/data/documents}") String baseDir) {
        this.baseDir = Path.of(baseDir);
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String store(String suggestedKey, byte[] content) {
        try {
            Path target = baseDir.resolve(suggestedKey).normalize();
            if (!target.startsWith(baseDir)) {
                // Blocks path traversal via a crafted filename (e.g. "../../etc/passwd").
                throw new IllegalArgumentException("Invalid storage key");
            }
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return suggestedKey;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            return Files.readAllBytes(baseDir.resolve(storageKey).normalize());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(baseDir.resolve(storageKey).normalize());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
