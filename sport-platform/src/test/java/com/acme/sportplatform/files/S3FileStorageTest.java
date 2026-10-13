package com.acme.sportplatform.files;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import com.acme.sportplatform.files.infrastructure.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class S3FileStorageTest {
    @Test void streamsCiphertextAndUsesApplicationDownloadGateway() throws Exception {
        var client = mock(S3Client.class);
        var crypto = new FileCryptography(Base64.getEncoder().encodeToString(new byte[32]), true, Clock.systemUTC());
        var storage = new S3FileStorage(client, "private-documents", crypto);
        String key = UUID.randomUUID().toString(); byte[] original = "%PDF-1.7\nprivate".getBytes();
        var encrypted = new AtomicReference<byte[]>();
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, PutObjectRequest.class); var body = invocation.getArgument(1, RequestBody.class);
            assertThat(request.bucket()).isEqualTo("private-documents");
            assertThat(request.contentType()).isEqualTo("application/octet-stream");
            assertThat(body.optionalContentLength()).contains((long)original.length + FileCryptography.ENVELOPE_OVERHEAD);
            encrypted.set(body.contentStreamProvider().newStream().readAllBytes()); return PutObjectResponse.builder().build();
        });
        storage.put(key, new ByteArrayInputStream(original), original.length);
        assertThat(encrypted.get()).isNotEqualTo(original);
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(), new ByteArrayInputStream(encrypted.get())));
        try (var input = storage.get(key)) { assertThat(input.readAllBytes()).isEqualTo(original); }
        assertThat(storage.presignedUrl(key, UUID.randomUUID(), UUID.randomUUID()).url()).startsWith("/api/v1/public/documents/");
        storage.delete(key); verify(client).deleteObject(any(DeleteObjectRequest.class));
    }
}
