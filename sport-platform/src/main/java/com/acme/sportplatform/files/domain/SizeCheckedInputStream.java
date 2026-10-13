package com.acme.sportplatform.files.domain;

import java.io.*;
import com.acme.sportplatform.common.exception.BusinessException;

/** Check actual bytes as well as the multipart declaration while copying with bounded buffers. */
public final class SizeCheckedInputStream extends FilterInputStream {
    private final long expected;
    private long count;
    public SizeCheckedInputStream(InputStream input, long expected) { super(input); this.expected = expected; }
    @Override public int read() throws IOException {
        int value = in.read(); check(value == -1 ? -1 : 1); return value;
    }
    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        int read = in.read(bytes, offset, length); check(read); return read;
    }
    private void check(int read) {
        if (read == -1) {
            if (count != expected) throw mismatch();
            return;
        }
        count += read;
        if (count > FileTypeValidator.MAX_BYTES) throw new BusinessException("files.too_large", "Размер документа превышает 10 МБ");
        if (count > expected) throw mismatch();
    }
    private BusinessException mismatch() { return new BusinessException("files.invalid_size", "Размер документа не соответствует переданному файлу"); }
}
