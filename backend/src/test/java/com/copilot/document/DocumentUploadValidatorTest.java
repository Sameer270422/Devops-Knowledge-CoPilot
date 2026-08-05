package com.copilot.document;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class DocumentUploadValidatorTest {

    private final DocumentUploadValidator validator = new DocumentUploadValidator(20);

    @Test
    void acceptsAGenuineTextFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "runbook.txt", "text/plain", "Some genuine runbook content.".getBytes());
        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void acceptsAGenuinePdf() {
        byte[] content = "%PDF-1.4\n%fake but correctly-prefixed pdf body for this test".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", content);
        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void rejectsAFileClaimingToBePdfButIsnt() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "not actually a pdf".getBytes());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(file));
    }

    @Test
    void rejectsARenamedBinaryMasqueradingAsText() {
        // Simulates the real attack this check exists for: an executable (or any binary
        // blob) renamed to look like a harmless text file. A NUL byte in the first bytes
        // is something no genuine text file contains but most binaries do.
        byte[] binary = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x03, 0x00}; // starts like a Windows PE/MZ header
        MockMultipartFile file = new MockMultipartFile("file", "totally-a-log.log", "text/plain", binary);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(file));
    }

    @Test
    void rejectsAnUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.sh", "text/plain", "echo hi".getBytes());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(file));
    }

    @Test
    void rejectsAnEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(file));
    }

    @Test
    void rejectsAFileOverTheSizeLimit() {
        DocumentUploadValidator tinyLimitValidator = new DocumentUploadValidator(0); // 0 MB max
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.txt", "text/plain", "still just a few bytes".getBytes());
        assertThrows(IllegalArgumentException.class, () -> tinyLimitValidator.validate(file));
    }
}
