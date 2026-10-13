package com.acme.sportplatform.files.infrastructure;

import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.files.FileLookup.SignedDownload;
import com.acme.sportplatform.files.domain.FileStorage;

@Component
@ConditionalOnProperty(name = "app.files.provider", havingValue = "s3")
public class S3FileStorage implements FileStorage, AutoCloseable {
    private final S3Client client;
    private final String bucket;
    private final FileCryptography cryptography;
    @org.springframework.beans.factory.annotation.Autowired
    public S3FileStorage(@Value("${app.files.s3.endpoint}") String endpoint,
            @Value("${app.files.s3.bucket}") String bucket,
            @Value("${app.files.s3.region:ru-1}") String region,
            @Value("${app.files.s3.access-key}") String accessKey,
            @Value("${app.files.s3.secret-key}") String secretKey, FileCryptography cryptography) {
        this(S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build(), bucket, cryptography);
    }
    public S3FileStorage(S3Client client, String bucket, FileCryptography cryptography) {
        this.client = client; this.bucket = bucket; this.cryptography = cryptography;
    }
    @Override public void put(String key, byte[] bytes) {
        try { client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType("application/octet-stream").build(), RequestBody.fromBytes(cryptography.encrypt(key, bytes))); }
        catch (software.amazon.awssdk.core.exception.SdkException ex) { throw unavailable(); }
    }
    @Override public byte[] get(String key) {
        try { return cryptography.decrypt(key, client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray()); }
        catch (NoSuchKeyException ex) { throw new BusinessException("files.not_found", "Файл не найден"); }
        catch (software.amazon.awssdk.core.exception.SdkException ex) { throw unavailable(); }
    }
    @Override public void delete(String key) {
        try { client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()); }
        catch (software.amazon.awssdk.core.exception.SdkException ex) { throw unavailable(); }
    }
    // Both providers expose the application gateway: S3 stores ciphertext, never a public plaintext URL.
    @Override public SignedDownload presignedUrl(String key, UUID documentId, UUID userId) {
        return cryptography.presignedUrl(key, documentId, userId);
    }
    private BusinessException unavailable() { return new BusinessException("files.storage_unavailable", "Хранилище файлов недоступно"); }
    @Override public void close() { client.close(); }
}
