package com.acme.sportplatform.files;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.files.domain.FileTypeValidator;
import com.acme.sportplatform.files.infrastructure.*;
import static org.assertj.core.api.Assertions.*;

class FileStorageTest {
    private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    @TempDir Path directory;
    FileCryptography crypto() { return new FileCryptography(TEST_KEY, false, Clock.fixed(NOW, ZoneOffset.UTC)); }
    @Test void encryptsAtRestWithUniqueNoncesAndRejectsTampering() throws Exception {
        var storage = new LocalFileStorage(directory.toString(), crypto());
        byte[] data = "%PDF-1.7\nprivate medical document".getBytes();
        String first = UUID.randomUUID().toString(), second = UUID.randomUUID().toString();
        storage.put(first, data); storage.put(second, data);
        byte[] ciphertext = Files.readAllBytes(directory.resolve(first));
        assertThat(ciphertext).isNotEqualTo(data).isNotEqualTo(Files.readAllBytes(directory.resolve(second)));
        assertThat(storage.get(first)).isEqualTo(data);
        var restarted = new LocalFileStorage(directory.toString(), crypto());
        assertThat(restarted.get(first)).isEqualTo(data);
        ciphertext[ciphertext.length - 1] ^= 1;
        Files.write(directory.resolve(first), ciphertext);
        assertThatThrownBy(() -> storage.get(first)).isInstanceOf(BusinessException.class);
        storage.delete(first); storage.delete(first);
        assertThatThrownBy(() -> storage.get(first)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage.get("../secret")).isInstanceOf(BusinessException.class);
    }
    @Test void requiresValidProductionKey() {
        assertThatThrownBy(() -> new FileCryptography("", true, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FileCryptography("not-base64!", true, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FileCryptography("AA==", true, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new FileCryptography(TEST_KEY, true, Clock.systemUTC())).doesNotThrowAnyException();
    }
    @Test void signaturesExpireAndAreBoundToFileAndUser() {
        var crypto = crypto(); var user = UUID.randomUUID(); var document = UUID.randomUUID(); var key = UUID.randomUUID().toString();
        var link = crypto.presignedUrl(key, document, user);
        assertThat(link.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        String signature = link.url().split("signature=")[1];
        long expires = link.expiresAt().getEpochSecond();
        crypto.verify(key, user, expires, signature);
        assertThatThrownBy(() -> crypto.verify(key, UUID.randomUUID(), expires, signature)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> crypto.verify("other", user, expires, signature)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> crypto.verify(key, user, expires + 1, signature)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> crypto.verify(key, user, expires, signature + "x")).isInstanceOf(BusinessException.class);
        var later = new FileCryptography(TEST_KEY, true, Clock.fixed(NOW.plusSeconds(600), ZoneOffset.UTC));
        assertThatThrownBy(() -> later.verify(key, user, expires, signature)).isInstanceOf(BusinessException.class);
    }
    @Test void enforcesExtensionMimeMagicAndTenMiBBoundary() {
        byte[] pdf = "%PDF-1.7\n".getBytes();
        assertThat(FileTypeValidator.validate(pdf, "medical.PDF", "application/pdf")).isEqualTo("application/pdf");
        assertThat(FileTypeValidator.validate(new byte[]{(byte)255,(byte)216,(byte)255,1}, "scan.jpeg", "image/jpeg")).isEqualTo("image/jpeg");
        assertThat(FileTypeValidator.validate(new byte[]{(byte)137,80,78,71,13,10,26,10}, "scan.png", "image/png")).isEqualTo("image/png");
        assertThatThrownBy(() -> FileTypeValidator.validate(pdf, "medical.exe", "application/pdf")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> FileTypeValidator.validate(pdf, "medical.jpg", "image/jpeg")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> FileTypeValidator.validate(pdf, "medical.pdf", "image/png")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> FileTypeValidator.validate(new byte[0], "empty.pdf", "application/pdf")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> FileTypeValidator.validate(new byte[]{37}, "short.pdf", "application/pdf")).isInstanceOf(BusinessException.class);
        byte[] maximum = Arrays.copyOf(pdf, FileTypeValidator.MAX_BYTES);
        assertThat(FileTypeValidator.validate(maximum, "max.pdf", null)).isEqualTo("application/pdf");
        assertThatThrownBy(() -> FileTypeValidator.validate(Arrays.copyOf(maximum, maximum.length + 1), "big.pdf", null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("files.too_large"));
    }
}
