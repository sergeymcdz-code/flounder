package com.acme.sportplatform.files.infrastructure;

import java.io.IOException;
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
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.cryptography = cryptography;
    }
    private Path path(String key) {
        if (key == null || !key.matches("[0-9a-f-]{36}")) throw new BusinessException("files.not_found", "Файл не найден");
        Path path = directory.resolve(key);
        if (Files.isSymbolicLink(path)) throw new BusinessException("files.not_found", "Файл не найден");
        return path;
    }
    @Override public void put(String key, byte[] bytes) {
        try {
            Files.createDirectories(directory);
            Files.write(path(key), cryptography.encrypt(key, bytes), StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) { throw new BusinessException("files.storage_unavailable", "Хранилище файлов недоступно"); }
    }
    @Override public byte[] get(String key) {
        try { return cryptography.decrypt(key, Files.readAllBytes(path(key))); }
        catch (NoSuchFileException ex) { throw new BusinessException("files.not_found", "Файл не найден"); }
        catch (IOException ex) { throw new BusinessException("files.storage_unavailable", "Хранилище файлов недоступно"); }
    }
    @Override public void delete(String key) {
        try { Files.deleteIfExists(path(key)); }
        catch (IOException ex) { throw new BusinessException("files.storage_unavailable", "Хранилище файлов недоступно"); }
    }
    @Override public SignedDownload presignedUrl(String key, UUID documentId, UUID userId) {
        return cryptography.presignedUrl(key, documentId, userId);
    }
}
