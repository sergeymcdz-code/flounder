package com.acme.sportplatform.files;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.files.application.FileService;
import com.acme.sportplatform.files.domain.*;
import com.acme.sportplatform.files.infrastructure.*;
import com.acme.sportplatform.files.infrastructure.jpa.FileAccessLogRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FileStorageTest {
    private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    @TempDir Path directory;
    FileCryptography crypto() { return new FileCryptography(TEST_KEY, false, Clock.fixed(NOW, ZoneOffset.UTC)); }
    String validate(byte[] data, String name, String mime) throws IOException {
        return FileTypeValidator.validate(new PushbackInputStream(new ByteArrayInputStream(data), 8), data.length, name, mime);
    }
    byte[] read(LocalFileStorage storage, String key) throws IOException {
        try (var input = storage.get(key)) { return input.readAllBytes(); }
    }
    @Test void encryptsAtRestWithUniqueNoncesAndRejectsTampering() throws Exception {
        var storage = new LocalFileStorage(directory.toString(), crypto());
        byte[] data = "%PDF-1.7\nprivate medical document".getBytes();
        String first = UUID.randomUUID().toString(), second = UUID.randomUUID().toString();
        storage.put(first, new ByteArrayInputStream(data), data.length); storage.put(second, new ByteArrayInputStream(data), data.length);
        byte[] ciphertext = Files.readAllBytes(directory.resolve(first));
        assertThat(ciphertext).isNotEqualTo(data).isNotEqualTo(Files.readAllBytes(directory.resolve(second)));
        assertThat(ciphertext[0]).isEqualTo((byte)1);
        assertThat(read(storage, first)).isEqualTo(data);
        assertThat(read(new LocalFileStorage(directory.toString(), crypto()), first)).isEqualTo(data);
        ciphertext[ciphertext.length - 1] ^= 1; Files.write(directory.resolve(first), ciphertext);
        assertThatThrownBy(() -> read(storage, first)).isInstanceOf(IOException.class);
        storage.delete(first); storage.delete(first);
        assertThatThrownBy(() -> storage.get(first)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage.get("../secret")).isInstanceOf(BusinessException.class);
    }
    @Test void derivesIndependentEncryptionAndSignatureKeysWithoutChangingEnvelope() throws Exception {
        var storage = new LocalFileStorage(directory.toString(), crypto());
        byte[] data = "%PDF-1.7\nprivate".getBytes(); String key = UUID.randomUUID().toString();
        storage.put(key, new ByteArrayInputStream(data), data.length);
        byte[] envelope = Files.readAllBytes(directory.resolve(key));
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(derive("flounder-files-enc-v1"), "AES"),
                new GCMParameterSpec(128, Arrays.copyOfRange(envelope,1,13)));
        cipher.updateAAD(key.getBytes(StandardCharsets.UTF_8));
        assertThat(cipher.doFinal(Arrays.copyOfRange(envelope,13,envelope.length))).isEqualTo(data);
        var user = UUID.randomUUID(); var link = crypto().presignedUrl(key, UUID.randomUUID(), user);
        String message = "files-download-v1\n" + key + "\n" + user + "\n" + link.expiresAt().getEpochSecond();
        var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(derive("flounder-files-sig-v1"), "HmacSHA256"));
        assertThat(link.url().split("signature=")[1]).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8))));
        assertThat(derive("flounder-files-enc-v1")).isNotEqualTo(derive("flounder-files-sig-v1")).isNotEqualTo(new byte[32]);
    }
    byte[] derive(String label) throws Exception {
        var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(Base64.getDecoder().decode(TEST_KEY),"HmacSHA256"));
        return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
    }
    @Test void requiresValidProductionKey() {
        assertThatThrownBy(() -> new FileCryptography("", true, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FileCryptography("not-base64!", true, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FileCryptography("AA==", true, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new FileCryptography(TEST_KEY, true, Clock.systemUTC())).doesNotThrowAnyException();
    }
    @Test void signaturesExpireAndAreBoundToFileAndUser() {
        var crypto = crypto(); var user = UUID.randomUUID(); var key = UUID.randomUUID().toString();
        var link = crypto.presignedUrl(key, UUID.randomUUID(), user);
        assertThat(link.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        String signature = link.url().split("signature=")[1]; long expires = link.expiresAt().getEpochSecond();
        crypto.verify(key, user, expires, signature);
        assertThatThrownBy(() -> crypto.verify(key, UUID.randomUUID(), expires, signature)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> crypto.verify("other", user, expires, signature)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> crypto.verify(key, user, expires + 1, signature)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> crypto.verify(key, user, expires, signature + "x")).isInstanceOf(BusinessException.class);
        var later = new FileCryptography(TEST_KEY, true, Clock.fixed(NOW.plusSeconds(600), ZoneOffset.UTC));
        assertThatThrownBy(() -> later.verify(key, user, expires, signature)).isInstanceOf(BusinessException.class);
    }
    @Test void enforcesExtensionMimeAndMagicWithoutConsumingThePrefix() throws Exception {
        byte[] pdf = "%PDF-1.7\n".getBytes();
        var input = new PushbackInputStream(new ByteArrayInputStream(pdf), 8);
        assertThat(FileTypeValidator.validate(input, pdf.length, "medical.PDF", "application/pdf")).isEqualTo("application/pdf");
        assertThat(input.readAllBytes()).isEqualTo(pdf);
        assertThat(validate(new byte[]{(byte)255,(byte)216,(byte)255,1}, "scan.jpeg", "image/jpeg")).isEqualTo("image/jpeg");
        assertThat(validate(new byte[]{(byte)137,80,78,71,13,10,26,10}, "scan.png", "image/png")).isEqualTo("image/png");
        assertThatThrownBy(() -> validate(pdf, "medical.exe", "application/pdf")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validate(pdf, "medical.jpg", "image/jpeg")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validate(pdf, "medical.pdf", "image/png")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validate(new byte[0], "empty.pdf", "application/pdf")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validate(new byte[]{37}, "short.pdf", "application/pdf")).isInstanceOf(BusinessException.class);
    }
    @Test void streamsTenMiBAndRejectsIncorrectOrOversizedDeclarations() throws Exception {
        var storage = new LocalFileStorage(directory.toString(), crypto());
        var service = new FileService(storage, crypto(), mock(FileAccessLogRepository.class));
        var result = service.put(pdfStream(FileTypeValidator.MAX_BYTES), FileTypeValidator.MAX_BYTES, "large.pdf", "application/pdf", UUID.randomUUID(), UUID.randomUUID());
        assertThat(Files.size(directory.resolve(result.key()))).isEqualTo(FileTypeValidator.MAX_BYTES + FileCryptography.ENVELOPE_OVERHEAD);
        try (var decrypted = service.get(result.key(), UUID.randomUUID(), UUID.randomUUID())) {
            assertThat(decrypted.readNBytes(8)).isEqualTo("%PDF-1.7".getBytes());
            assertThat(decrypted.transferTo(OutputStream.nullOutputStream())).isEqualTo(FileTypeValidator.MAX_BYTES - 8);
        }
        assertThatThrownBy(() -> service.put(pdfStream(100), FileTypeValidator.MAX_BYTES + 1L, "large.pdf", null, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("files.too_large"));
        assertThatThrownBy(() -> service.put(pdfStream(100), 90, "bad.pdf", null, UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.put(pdfStream(90), 100, "bad.pdf", null, UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(BusinessException.class);
        try (var objects = Files.list(directory)) { assertThat(objects.count()).isEqualTo(1); }
        for (var method : FileLookup.class.getDeclaredMethods()) {
            assertThat(method.getReturnType()).isNotEqualTo(byte[].class);
            assertThat(method.getParameterTypes()).doesNotContain(byte[].class);
        }
    }
    @Test void publicLogosStayPlaintextAndCannotReadPrivateKeys() throws Exception {
        var storage = new LocalFileStorage(directory.toString(), crypto());
        var service = new FileService(storage, crypto(), mock(FileAccessLogRepository.class));
        byte[] png = new byte[]{(byte)137,80,78,71,13,10,26,10};
        var file = service.putPublic(new ByteArrayInputStream(png), png.length, "logo.png", "image/png");
        assertThat(Files.readAllBytes(directory.resolve(file.key()))).isEqualTo(png);
        try (var stream = service.getPublic(file.key())) { assertThat(stream.readAllBytes()).isEqualTo(png); }
        assertThatThrownBy(() -> service.getPublic(UUID.randomUUID().toString())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.putPublic(new ByteArrayInputStream("%PDF-1.7".getBytes()), 8, "logo.pdf", "application/pdf"))
                .isInstanceOf(BusinessException.class);
        service.deletePublic(file.key());
        assertThat(Files.exists(directory.resolve(file.key()))).isFalse();
    }
    InputStream pdfStream(long size) {
        return new SequenceInputStream(new ByteArrayInputStream("%PDF-1.7".getBytes()), new InputStream() {
            long remaining = size - 8;
            @Override public int read() { return remaining-- > 0 ? 65 : -1; }
            @Override public int read(byte[] bytes, int offset, int length) {
                if (length == 0) return 0; if (remaining <= 0) return -1;
                int count = (int)Math.min(length, remaining); Arrays.fill(bytes, offset, offset + count, (byte)65); remaining -= count; return count;
            }
        });
    }
}
