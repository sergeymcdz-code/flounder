package com.acme.sportplatform.files.infrastructure;

import java.io.*;
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
    public static final int ENVELOPE_OVERHEAD = 1 + 12 + 16;
    private final byte[] encryptionKey;
    private final byte[] signatureKey;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    public FileCryptography(@Value("${app.files.encryption-key:}") String configuredKey, Environment environment) {
        this(configuredKey, environment.matchesProfiles("prod"), Clock.systemUTC());
    }

    public FileCryptography(String configuredKey, boolean production, Clock clock) {
        this.clock = clock;
        byte[] masterKey;
        if (configuredKey == null || configuredKey.isBlank()) {
            if (production) throw new IllegalStateException("APP_FILES_ENCRYPTION_KEY обязателен в prod");
            masterKey = new byte[32];
            random.nextBytes(masterKey);
            org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                    "APP_FILES_ENCRYPTION_KEY не задан: временный ключ для разработки; задайте ключ для сохранения файлов между перезапусками");
        } else {
            try { masterKey = Base64.getDecoder().decode(configuredKey); }
            catch (IllegalArgumentException ex) { throw new IllegalStateException("APP_FILES_ENCRYPTION_KEY должен быть base64", ex); }
            if (masterKey.length != 32) throw new IllegalStateException("APP_FILES_ENCRYPTION_KEY должен содержать 32 байта");
        }
        encryptionKey = hmac(masterKey, "flounder-files-enc-v1");
        signatureKey = hmac(masterKey, "flounder-files-sig-v1");
        Arrays.fill(masterKey, (byte) 0);
    }

    public OutputStream encrypting(String fileKey, OutputStream output) throws IOException {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        Cipher cipher = cipher(Cipher.ENCRYPT_MODE, fileKey, nonce);
        output.write(1); output.write(nonce);
        return new CipherOutputStream(output, cipher);
    }

    public InputStream encrypting(String fileKey, InputStream input) {
        byte[] header = new byte[13];
        header[0] = 1;
        byte[] nonce = new byte[12]; random.nextBytes(nonce);
        System.arraycopy(nonce, 0, header, 1, nonce.length);
        return new SequenceInputStream(new ByteArrayInputStream(header),
                new CipherInputStream(input, cipher(Cipher.ENCRYPT_MODE, fileKey, nonce)));
    }

    public InputStream decrypting(String fileKey, InputStream input) throws IOException {
        byte[] header = input.readNBytes(13);
        if (header.length != 13 || header[0] != 1) throw corrupt();
        return new CipherInputStream(input, cipher(Cipher.DECRYPT_MODE, fileKey, Arrays.copyOfRange(header, 1, 13)));
    }

    private Cipher cipher(int mode, String fileKey, byte[] nonce) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(fileKey.getBytes(StandardCharsets.UTF_8));
            return cipher;
        } catch (GeneralSecurityException ex) { throw corrupt(); }
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
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(signatureKey,
                "files-download-v1\n" + fileKey + "\n" + userId + "\n" + expires));
    }
    private byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    private BusinessException corrupt() { return new BusinessException("files.corrupt", "Файл повреждён или не удалось его расшифровать"); }
}
