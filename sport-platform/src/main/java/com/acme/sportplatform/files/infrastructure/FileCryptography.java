package com.acme.sportplatform.files.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.files.FileLookup.SignedDownload;

@Component
public class FileCryptography {
    private final byte[] key;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    public FileCryptography(@Value("${app.files.encryption-key:}") String configuredKey, Environment environment) {
        this(configuredKey, environment.matchesProfiles("prod"), Clock.systemUTC());
    }

    public FileCryptography(String configuredKey, boolean production, Clock clock) {
        this.clock = clock;
        if (configuredKey == null || configuredKey.isBlank()) {
            if (production) throw new IllegalStateException("APP_FILES_ENCRYPTION_KEY обязателен в prod");
            key = new byte[32];
            random.nextBytes(key);
            org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                    "APP_FILES_ENCRYPTION_KEY не задан: временный ключ для разработки; задайте ключ для сохранения файлов между перезапусками");
        } else {
            try { key = Base64.getDecoder().decode(configuredKey); }
            catch (IllegalArgumentException ex) { throw new IllegalStateException("APP_FILES_ENCRYPTION_KEY должен быть base64", ex); }
            if (key.length != 32) throw new IllegalStateException("APP_FILES_ENCRYPTION_KEY должен содержать 32 байта");
        }
    }

    public byte[] encrypt(String fileKey, byte[] plaintext) {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, fileKey, nonce, plaintext);
        return ByteBuffer.allocate(1 + nonce.length + encrypted.length).put((byte) 1).put(nonce).put(encrypted).array();
    }

    public byte[] decrypt(String fileKey, byte[] encrypted) {
        if (encrypted.length < 29 || encrypted[0] != 1) throw new BusinessException("files.corrupt", "Файл повреждён");
        return crypt(Cipher.DECRYPT_MODE, fileKey, Arrays.copyOfRange(encrypted, 1, 13),
                Arrays.copyOfRange(encrypted, 13, encrypted.length));
    }

    private byte[] crypt(int mode, String fileKey, byte[] nonce, byte[] bytes) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(fileKey.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(bytes);
        } catch (GeneralSecurityException ex) {
            throw new BusinessException("files.corrupt", "Не удалось прочитать или зашифровать файл");
        }
    }

    public SignedDownload presignedUrl(String fileKey, UUID documentId, UUID userId) {
        Instant expiry = clock.instant().plusSeconds(600);
        return new SignedDownload("/api/v1/public/documents/" + documentId + "/content?expires="
                + expiry.getEpochSecond() + "&signature=" + sign(fileKey, userId, expiry.getEpochSecond()), expiry);
    }

    public void verify(String fileKey, UUID userId, long expires, String signature) {
        long now = clock.instant().getEpochSecond();
        if (userId == null || signature == null || expires <= now || expires > now + 600
                || !MessageDigest.isEqual(sign(fileKey, userId, expires).getBytes(StandardCharsets.UTF_8),
                        signature.getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException("files.not_found", "Файл не найден");
    }

    private String sign(String fileKey, UUID userId, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(
                    ("files-download-v1\n" + fileKey + "\n" + userId + "\n" + expires).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
}
