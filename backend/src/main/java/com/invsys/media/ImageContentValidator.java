package com.invsys.media;

import com.invsys.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Magic-byte + content-type allowlist for image uploads (rejects polyglots / spoofed MIME).
 */
@Component
public class ImageContentValidator {

    private static final Set<String> ALLOWED = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private static final Map<String, String> EXT_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    public String detectAndValidate(byte[] bytes, String declaredContentType) {
        if (bytes == null || bytes.length < 12) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "File too small or empty");
        }
        String detected = detect(bytes);
        if (detected == null || !ALLOWED.contains(detected)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE",
                    "Only JPEG, PNG, WebP, and GIF images are allowed");
        }
        if (declaredContentType != null && !declaredContentType.isBlank()) {
            String normalized = declaredContentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
            if (!normalized.equals(detected) && !compatibleJpeg(normalized, detected)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_TYPE_MISMATCH",
                        "Declared content type does not match file contents");
            }
        }
        return detected;
    }

    public String extensionFor(String contentType) {
        return EXT_BY_TYPE.getOrDefault(contentType, "bin");
    }

    public boolean isAllowedContentType(String contentType) {
        return contentType != null && ALLOWED.contains(contentType.toLowerCase(Locale.ROOT).split(";")[0].trim());
    }

    private static boolean compatibleJpeg(String declared, String detected) {
        return detected.equals("image/jpeg") && (declared.equals("image/jpg") || declared.equals("image/pjpeg"));
    }

    static String detect(byte[] bytes) {
        // JPEG
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        // PNG
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "image/png";
        }
        // GIF
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') {
            return "image/gif";
        }
        // WebP: RIFF....WEBP
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
