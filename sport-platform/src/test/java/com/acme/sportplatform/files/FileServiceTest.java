package com.acme.sportplatform.files;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.files.application.FileService;
import com.acme.sportplatform.files.domain.FileStorage;
import com.acme.sportplatform.files.infrastructure.FileCryptography;
import com.acme.sportplatform.files.infrastructure.jpa.FileAccessLogRepository;
import java.io.*;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileServiceTest {
    private final FileStorage storage = mock(FileStorage.class);
    private final FileAccessLogRepository logs = mock(FileAccessLogRepository.class);
    private final FileService files = new FileService(storage,
            new FileCryptography("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", false, Clock.systemUTC()), logs);

    @Test void runtimeReadFailureBecomesCorruptAndClosesStreamBeforeAnyDownloadAudit() throws Exception {
        var broken = spy(new InputStream() {
            @Override public int read() { throw new IllegalStateException("decryption failure"); }
        });
        when(storage.get("key")).thenReturn(broken);
        assertThatThrownBy(() -> files.get("key", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("files.corrupt"));
        verify(broken).close();
        verifyNoInteractions(logs);
    }
    @Test void anonymousDenialIsNotPersisted() {
        files.logDenied("key", UUID.randomUUID(), null);
        verifyNoInteractions(logs);
    }
}
