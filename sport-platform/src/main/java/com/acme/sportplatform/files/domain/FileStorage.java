package com.acme.sportplatform.files.domain;

import java.io.InputStream;
import java.util.UUID;
import com.acme.sportplatform.files.FileLookup.SignedDownload;

public interface FileStorage {
    void putPublic(String key, InputStream input, long size);
    InputStream getPublic(String key);
    void put(String key, InputStream input, long size);
    InputStream get(String key);
    void delete(String key);
    SignedDownload presignedUrl(String key, UUID documentId, UUID userId);
}
