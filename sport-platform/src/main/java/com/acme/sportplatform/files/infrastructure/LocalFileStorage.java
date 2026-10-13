package com.acme.sportplatform.files.infrastructure;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.files.FileLookup.SignedDownload;
import com.acme.sportplatform.files.domain.FileStorage;

@Component
@ConditionalOnProperty(name = "app.files.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {
    private final Path directory;
    private final FileCryptography cryptography;
    public LocalFileStorage(@Value("${app.files.local-dir:./data/files}") String directory, FileCryptography cryptography) {
        this.directory = Path.of(directory).toAbsolutePath().normalize(); this.cryptography = cryptography;
    }
    private Path path(String key) {
        if (key == null || !key.matches("[0-9a-f-]{36}")) throw new BusinessException("files.not_found", "Файл не найден");
        Path path = directory.resolve(key);
        if (Files.isSymbolicLink(path)) throw new BusinessException("files.not_found", "Файл не найден");
        return path;
    }
    @Override public void put(String key, InputStream input, long size) {
        Path target = path(key); boolean created = false;
        try {
            Files.createDirectories(directory);
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                created = true;
                try (var encrypted = cryptography.encrypting(key, output)) {
                    if (input.transferTo(encrypted) != size) throw new BusinessException("files.invalid_size", "Размер документа не соответствует переданному файлу");
                }
            }
        } catch (IOException | RuntimeException ex) {
            if (created) {
                try { Files.deleteIfExists(target); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            }
            if (ex instanceof BusinessException business) throw business;
            throw new BusinessException("files.storage_unavailable", "Хранилище файлов недоступно");
        }
    }
    @Override public InputStream get(String key) {
        InputStream input = null;
        try {
            input = Files.newInputStream(path(key));
            return cryptography.decrypting(key, input);
        } catch (IOException | RuntimeException ex) {
            if (input != null) try { input.close(); } catch (IOException close) { ex.addSuppressed(close); }
            if (ex instanceof BusinessException business) throw business;
            if (ex instanceof NoSuchFileException) throw new BusinessException("files.not_found", "Файл не найден");
            throw new BusinessException("files.storage_unavailable", "Хранилище файлов недоступно");
        }
    }
    @Override public void delete(String key) {
        try { Files.deleteIfExists(path(key)); }
        catch (IOException ex) { throw new BusinessException("files.storage_unavailable", "Хранилище файлов недоступно"); }
    }
    @Override public SignedDownload presignedUrl(String key, UUID documentId, UUID userId) {
        return cryptography.presignedUrl(key, documentId, userId);
    }
}
