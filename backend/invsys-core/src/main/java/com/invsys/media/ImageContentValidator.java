package com.invsys.media;

import com.invsys.core.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Magic-byte + content-type allowlist for uploads (rejects polyglots / spoofed MIME).
 * Raster images, sanitized SVG, and PDF are accepted; client {@code Content-Type} is never trusted alone.
 */
@Component
public class ImageContentValidator {

    private static final Set<String> ALLOWED = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml", "application/pdf");

    private static final Map<String, String> EXT_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif",
            "image/svg+xml", "svg",
            "application/pdf", "pdf");

    private static final Pattern SVG_UNSAFE = Pattern.compile(
            "(?i)<script|</script|onload\\s*=|onerror\\s*=|onmouseover\\s*=|onclick\\s*=|"
                    + "xlink:href|javascript:|data:text/html|<!ENTITY|<!DOCTYPE|SYSTEM\\s+[\"']");

    public String detectAndValidate(byte[] bytes, String declaredContentType) {
        if (bytes == null || bytes.length < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "File too small or empty");
        }
        String detected = detect(bytes);
        if (detected == null || !ALLOWED.contains(detected)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE",
                    "Only JPEG, PNG, WebP, GIF, SVG, and PDF files are allowed");
        }
        if ("image/svg+xml".equals(detected)) {
            sanitizeSvg(bytes);
        }
        if (declaredContentType != null && !declaredContentType.isBlank()) {
            String normalized = declaredContentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
            if (!normalized.equals(detected) && !compatibleJpeg(normalized, detected)
                    && !compatibleSvg(normalized, detected)) {
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

    private static boolean compatibleSvg(String declared, String detected) {
        return detected.equals("image/svg+xml") && (declared.equals("image/svg") || declared.equals("text/xml"));
    }

    static void sanitizeSvg(byte[] bytes) {
        String xml = new String(bytes, StandardCharsets.UTF_8);
        if (xml.indexOf('\0') >= 0 || SVG_UNSAFE.matcher(xml).find()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSAFE_SVG",
                    "SVG contains script, event handlers, or external entity markup");
        }
    }

    static String detect(byte[] bytes) {
        // ELF / PE executables disguised as images
        if (bytes.length >= 4 && bytes[0] == 0x7F && bytes[1] == 'E' && bytes[2] == 'L' && bytes[3] == 'F') {
            return null;
        }
        if (bytes.length >= 2 && bytes[0] == 'M' && bytes[1] == 'Z') {
            return null;
        }
        // JPEG
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        // PNG
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "image/png";
        }
        // GIF
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') {
            return "image/gif";
        }
        // WebP: RIFF....WEBP
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        // PDF
        if (bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') {
            return "application/pdf";
        }
        // SVG (text XML)
        if (looksLikeSvg(bytes)) {
            return "image/svg+xml";
        }
        return null;
    }

    private static boolean looksLikeSvg(byte[] bytes) {
        int len = Math.min(bytes.length, 2048);
        String head = new String(bytes, 0, len, StandardCharsets.UTF_8).stripLeading();
        if (head.startsWith("\uFEFF")) {
            head = head.substring(1).stripLeading();
        }
        String lower = head.toLowerCase(Locale.ROOT);
        return lower.startsWith("<svg") || (lower.startsWith("<?xml") && lower.contains("<svg"));
    }
}
