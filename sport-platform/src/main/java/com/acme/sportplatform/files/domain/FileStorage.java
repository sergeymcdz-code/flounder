package com.acme.sportplatform.files.domain;

import java.util.UUID;
import com.acme.sportplatform.files.FileLookup.SignedDownload;

public interface FileStorage {
    void put(String key, byte[] bytes);
    byte[] get(String key);
    void delete(String key);
    SignedDownload presignedUrl(String key, UUID documentId, UUID userId);
}
