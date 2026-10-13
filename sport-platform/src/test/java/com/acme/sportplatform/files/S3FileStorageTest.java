package com.acme.sportplatform.files;

import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import com.acme.sportplatform.files.infrastructure.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class S3FileStorageTest {
    @Test void storesCiphertextAndUsesApplicationDownloadGateway() throws Exception {
        var client = mock(S3Client.class);
        var crypto = new FileCryptography(Base64.getEncoder().encodeToString(new byte[32]), true, Clock.systemUTC());
        var storage = new S3FileStorage(client, "private-documents", crypto);
        String key = UUID.randomUUID().toString(); byte[] original = "%PDF-1.7\nprivate".getBytes();
        storage.put(key, original);
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("private-documents");
        assertThat(request.getValue().contentType()).isEqualTo("application/octet-stream");
        byte[] encrypted = body.getValue().contentStreamProvider().newStream().readAllBytes();
        assertThat(encrypted).isNotEqualTo(original);
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), encrypted));
        assertThat(storage.get(key)).isEqualTo(original);
        assertThat(storage.presignedUrl(key, UUID.randomUUID(), UUID.randomUUID()).url()).startsWith("/api/v1/public/documents/");
        storage.delete(key); verify(client).deleteObject(any(DeleteObjectRequest.class));
    }
}
