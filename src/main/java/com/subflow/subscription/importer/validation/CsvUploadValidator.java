package com.subflow.subscription.importer.validation;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * 업로드된 CSV 파일의 형식과 크기를 검증하고 바이트를 반환한다.
 */
public class CsvUploadValidator {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/csv",
            "text/plain",
            "application/vnd.ms-excel",
            "application/octet-stream"
    );

    public byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_IMPORT_FILE);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
        if (!hasCsvExtension(file.getOriginalFilename())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_CSV);
        }
        if (!isAllowedContentType(file.getContentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_CSV);
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_CSV);
        }
        if (containsNul(content)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_CSV);
        }
        return content;
    }

    private boolean hasCsvExtension(String filename) {
        if (filename == null) {
            return false;
        }
        int separator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String basename = filename.substring(separator + 1);
        return basename.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    private boolean isAllowedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        int parameterStart = contentType.indexOf(';');
        String mediaType = parameterStart >= 0 ? contentType.substring(0, parameterStart) : contentType;
        return ALLOWED_CONTENT_TYPES.contains(mediaType.trim().toLowerCase(Locale.ROOT));
    }

    private boolean containsNul(byte[] content) {
        for (byte value : content) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }
}
