package com.acme.sportplatform.files.domain;

import java.util.*;
import com.acme.sportplatform.common.exception.BusinessException;

public final class FileTypeValidator {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private FileTypeValidator() {}
    public static String validate(byte[] bytes, String name, String declaredType) {
        if (bytes.length > MAX_BYTES) throw new BusinessException("files.too_large", "Размер документа превышает 10 МБ");
        if (bytes.length == 0) throw new BusinessException("files.empty", "Документ пуст");
        String extension = name == null || !name.contains(".") ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String expected = switch (extension) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> throw new BusinessException("files.invalid_type", "Разрешены только PDF, JPG и PNG");
        };
        if (declaredType != null && !declaredType.isBlank() && !declaredType.equals("application/octet-stream") && !expected.equals(declaredType))
            throw new BusinessException("files.invalid_type", "Тип документа не соответствует расширению");
        byte[] magic = switch (extension) {
            case "pdf" -> new byte[]{0x25, 0x50, 0x44, 0x46, 0x2d};
            case "jpg", "jpeg" -> new byte[]{(byte)0xff, (byte)0xd8, (byte)0xff};
            default -> new byte[]{(byte)0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        };
        if (bytes.length < magic.length || !Arrays.equals(Arrays.copyOf(bytes, magic.length), magic))
            throw new BusinessException("files.invalid_magic", "Содержимое документа не соответствует PDF, JPG или PNG");
        return expected;
    }
}
