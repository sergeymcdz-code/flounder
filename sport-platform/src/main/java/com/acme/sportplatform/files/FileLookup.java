package com.acme.sportplatform.files;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

/** Public boundary for encrypted streaming storage and signed downloads. */
public interface FileLookup {
    StoredFile put(InputStream input, long size, String originalName, String contentType, UUID documentId, UUID userId);
    InputStream get(String key, UUID documentId, UUID userId);
    void delete(String key, UUID documentId, UUID userId);
    SignedDownload presignedUrl(String key, UUID documentId, UUID userId);
    void verify(String key, UUID userId, long expires, String signature);
    void logDenied(String key, UUID documentId, UUID userId);
    record StoredFile(String key, String contentType, long sizeBytes) {}
    record SignedDownload(String url, Instant expiresAt) {}
}
