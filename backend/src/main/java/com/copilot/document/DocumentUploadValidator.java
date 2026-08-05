package com.copilot.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * First line of defense from the security plan: "file type allowlist, size limits ...
 * before a document enters the pipeline." Virus/malware scanning (e.g. ClamAV) is called
 * out in the same plan but is NOT implemented here — it needs an external scanning
 * service this environment can't stand up. See docs/06-security-hardening.md for that gap;
 * treat it as a known follow-up before accepting untrusted uploads in a real deployment,
 * not as done. What Stage 6 does add is content sniffing (below) so extension-allowlisting
 * isn't the *only* check — a renamed executable no longer passes just by being called
 * "notes.txt".
 */
@Component
public class DocumentUploadValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".txt", ".md", ".log", ".csv");
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46}; // "%PDF"
    private static final int SNIFF_BYTES = 512;

    private final long maxSizeBytes;

    public DocumentUploadValidator(@Value("${app.upload.max-size-mb:20}") long maxSizeMb) {
        this.maxSizeBytes = maxSizeMb * 1024 * 1024;
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException("File exceeds the maximum upload size");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename is missing");
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
        if (!allowed) {
            throw new IllegalArgumentException("Unsupported file type. Allowed: " + ALLOWED_EXTENSIONS);
        }
        validateContentMatchesExtension(file, lower);
    }

    // Extension allowlisting alone doesn't stop someone renaming an .exe or a .zip to
    // "notes.txt" — a real, exploitable gap the extension check by itself leaves open.
    // This doesn't replace real malware scanning (still a known gap), but it closes the
    // cheapest version of the attack by sniffing actual bytes rather than trusting the
    // filename the browser sent.
    private void validateContentMatchesExtension(MultipartFile file, String lowerFilename) {
        byte[] head = readHead(file);
        if (lowerFilename.endsWith(".pdf")) {
            if (!startsWith(head, PDF_MAGIC)) {
                throw new IllegalArgumentException("File content doesn't match a valid PDF");
            }
            return;
        }
        // .txt/.md/.log/.csv have no fixed magic number, so instead reject anything that
        // doesn't look like text: a NUL byte in the first 512 bytes is something
        // essentially no genuine text file ever contains, but nearly every binary format
        // (executables, archives, images) does within its first few hundred bytes. This is
        // the same heuristic tools like git and grep -I use to detect binary content.
        for (byte b : head) {
            if (b == 0) {
                throw new IllegalArgumentException("File content doesn't look like a text file");
            }
        }
    }

    private byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] buffer = new byte[SNIFF_BYTES];
            int read = in.read(buffer);
            return read <= 0 ? new byte[0] : Arrays.copyOf(buffer, read);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read uploaded file");
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}
