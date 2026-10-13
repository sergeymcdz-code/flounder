package com.acme.sportplatform.files;

import java.time.Instant;
import java.util.UUID;

/** Public boundary for encrypted file storage and signed downloads. */
public interface FileLookup {
    StoredFile put(byte[] bytes, String originalName, String contentType, UUID userId);
    byte[] get(String key, UUID userId);
    void delete(String key, UUID userId);
    SignedDownload presignedUrl(String key, UUID documentId, UUID userId);
    void verify(String key, UUID userId, long expires, String signature);
    record StoredFile(String key, String contentType, long sizeBytes) {}
    record SignedDownload(String url, Instant expiresAt) {}
}
