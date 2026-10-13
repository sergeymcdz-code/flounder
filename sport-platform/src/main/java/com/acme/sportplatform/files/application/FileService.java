package com.acme.sportplatform.files.application;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.files.FileLookup;
import com.acme.sportplatform.files.domain.*;
import com.acme.sportplatform.files.infrastructure.FileCryptography;
import com.acme.sportplatform.files.infrastructure.jpa.*;

@Service
@Transactional
public class FileService implements FileLookup {
    private final FileStorage storage;
    private final FileCryptography cryptography;
    private final FileAccessLogRepository logs;
    public FileService(FileStorage storage, FileCryptography cryptography, FileAccessLogRepository logs) {
        this.storage = storage; this.cryptography = cryptography; this.logs = logs;
    }
    @Override public StoredFile put(byte[] bytes, String originalName, String contentType, UUID userId) {
        String detected = FileTypeValidator.validate(bytes, originalName, contentType);
        String key = UUID.randomUUID().toString();
        storage.put(key, bytes);
        // Undo the object write if metadata or event publication rolls back.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) storage.delete(key);
                    }
                });
        log(key, userId, "UPLOAD");
        return new StoredFile(key, detected, bytes.length);
    }
    @Override public byte[] get(String key, UUID userId) {
        byte[] bytes = storage.get(key);
        log(key, userId, "DOWNLOAD");
        return bytes;
    }
    @Override public void delete(String key, UUID userId) {
        log(key, userId, "DELETE");
        // Keep the object if the metadata deletion rolls back.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { storage.delete(key); }
                });
        else storage.delete(key);
    }
    @Override public SignedDownload presignedUrl(String key, UUID documentId, UUID userId) {
        log(key, userId, "SIGN_DOWNLOAD");
        return storage.presignedUrl(key, documentId, userId);
    }
    @Override public void verify(String key, UUID userId, long expires, String signature) {
        cryptography.verify(key, userId, expires, signature);
    }
    private void log(String key, UUID userId, String action) {
        var log = new FileAccessLogEntity();
        log.setId(UUID.randomUUID()); log.setFileKey(key); log.setUserId(userId);
        log.setAction(action); log.setAt(OffsetDateTime.now()); logs.save(log);
    }
}
