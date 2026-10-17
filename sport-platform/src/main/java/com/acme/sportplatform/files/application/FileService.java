package com.acme.sportplatform.files.application;

import java.io.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.exception.BusinessException;
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
    @Override public StoredFile putPublic(InputStream input, long size, String originalName, String contentType) {
        if (size > 2 * 1024 * 1024) throw new BusinessException("files.image_too_large", "Размер логотипа превышает 2 МБ");
        var checked = new PushbackInputStream(new SizeCheckedInputStream(input, size), 8);
        String detected;
        try { detected = FileTypeValidator.validate(checked, size, originalName, contentType); }
        catch (IOException ex) { throw new BusinessException("files.read_failed", "Не удалось прочитать логотип"); }
        if (!detected.equals("image/png") && !detected.equals("image/jpeg"))
            throw new BusinessException("files.invalid_image_type", "Разрешены только PNG и JPG");
        String key = "public-" + UUID.randomUUID();
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) storage.delete(key); }
                });
        storage.putPublic(key, checked, size);
        return new StoredFile(key, detected, size);
    }
    @Override public InputStream getPublic(String key) {
        requirePublic(key); return storage.getPublic(key);
    }
    @Override public void deletePublic(String key) {
        requirePublic(key);
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { storage.delete(key); }
                });
        else storage.delete(key);
    }
    private void requirePublic(String key) {
        if (key == null || !key.matches("public-[0-9a-f-]{36}")) throw new BusinessException("files.not_found", "Файл не найден");
    }
    @Override public StoredFile put(InputStream input, long size, String originalName, String contentType, UUID documentId, UUID userId) {
        var checked = new PushbackInputStream(new SizeCheckedInputStream(input, size), 8);
        String detected;
        try { detected = FileTypeValidator.validate(checked, size, originalName, contentType); }
        catch (IOException ex) { throw new BusinessException("files.read_failed", "Не удалось прочитать документ"); }
        String key = UUID.randomUUID().toString();
        // Register before writing: failures during an object upload also trigger cleanup.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) storage.delete(key);
                    }
                });
        storage.put(key, checked, size);
        log(key, documentId, userId, "UPLOAD");
        return new StoredFile(key, detected, size);
    }
    @Override public InputStream get(String key, UUID documentId, UUID userId) {
        InputStream input = storage.get(key);
        try { log(key, documentId, userId, "DOWNLOAD"); return input; }
        catch (RuntimeException ex) {
            try { input.close(); } catch (IOException close) { ex.addSuppressed(close); }
            throw ex;
        }
    }
    @Override public void delete(String key, UUID documentId, UUID userId) {
        log(key, documentId, userId, "DELETE");
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { storage.delete(key); }
                });
        else storage.delete(key);
    }
    @Override public SignedDownload presignedUrl(String key, UUID documentId, UUID userId) {
        log(key, documentId, userId, "SIGN_DOWNLOAD");
        return storage.presignedUrl(key, documentId, userId);
    }
    @Override public void verify(String key, UUID userId, long expires, String signature) {
        cryptography.verify(key, userId, expires, signature);
    }
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDenied(String key, UUID documentId, UUID userId) {
        // No document FK: preserve denied attempts even after deletion, and record unknown IDs.
        log(key, documentId, userId, "DENIED");
    }
    private void log(String key, UUID documentId, UUID userId, String action) {
        var log = new FileAccessLogEntity();
        log.setId(UUID.randomUUID()); log.setFileKey(key); log.setDocumentId(documentId); log.setUserId(userId);
        log.setAction(action); log.setAt(OffsetDateTime.now()); logs.save(log);
    }
}
