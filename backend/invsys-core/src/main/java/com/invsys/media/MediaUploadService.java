package com.invsys.media;

import com.invsys.core.common.ApiException;
import com.invsys.domain.MediaObject;
import com.invsys.repository.MediaAttachmentRepository;
import com.invsys.repository.MediaObjectRepository;
import com.invsys.modules.catalog.repository.ProductMediaRepository;
import com.invsys.repository.UserRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import com.invsys.domain.Tenant;

@Service
public class MediaUploadService {

    public enum UploadKind {
        AVATAR, PRODUCT, EVIDENCE, LOCATION
    }

    private final MediaObjectRepository mediaObjectRepository;
    private final MediaAttachmentRepository mediaAttachmentRepository;
    private final ProductMediaRepository productMediaRepository;
    private final UserRepository userRepository;
    private final ObjectStorage objectStorage;
    private final ImageContentValidator contentValidator;
    private final MediaStorageProperties properties;

    public MediaUploadService(MediaObjectRepository mediaObjectRepository,
                              MediaAttachmentRepository mediaAttachmentRepository,
                              ProductMediaRepository productMediaRepository,
                              UserRepository userRepository,
                              ObjectStorage objectStorage,
                              ImageContentValidator contentValidator,
                              MediaStorageProperties properties) {
        this.mediaObjectRepository = mediaObjectRepository;
        this.mediaAttachmentRepository = mediaAttachmentRepository;
        this.productMediaRepository = productMediaRepository;
        this.userRepository = userRepository;
        this.objectStorage = objectStorage;
        this.contentValidator = contentValidator;
        this.properties = properties;
    }

    @Transactional
    public MediaObject upload(MultipartFile file, UploadKind kind) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "File is required");
        }
        long max = switch (kind) {
            case AVATAR -> properties.getAvatarMaxBytes();
            case PRODUCT, EVIDENCE, LOCATION -> properties.getEvidenceMaxBytes();
        };
        if (file.getSize() > max || file.getSize() > properties.getMaxBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "File exceeds limit of " + max + " bytes");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "READ_FAILED", "Unable to read upload");
        }
        if (bytes.length > max) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "File exceeds limit of " + max + " bytes");
        }

        String contentType = contentValidator.detectAndValidate(bytes, file.getContentType());
        String checksum = sha256(bytes);
        UUID tenantId = TenantContext.requireTenantId();
        String ext = contentValidator.extensionFor(contentType);

        MediaObject media = new MediaObject();
        media.setTenantId(tenantId);
        media.setStorageKey("pending/" + UUID.randomUUID() + "." + ext);
        media.setContentType(contentType);
        media.setByteSize(bytes.length);
        media.setChecksumSha256(checksum);
        media.setOriginalFilename(sanitizeFilename(file.getOriginalFilename()));
        media.setCreatedBy(TenantContext.getUserId().orElse(null));
        media = mediaObjectRepository.saveAndFlush(media);

        String storageKey = tenantId + "/" + media.getId() + "." + ext;
        objectStorage.put(storageKey, bytes, contentType);
        media.setStorageKey(storageKey);
        return mediaObjectRepository.save(media);
    }

    @Transactional(readOnly = true)
    public MediaObject requireOwned(UUID mediaId) {
        UUID tenantId = TenantContext.requireTenantId();
        return mediaObjectRepository.findByTenantIdAndId(tenantId, mediaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Media not found"));
    }

    /** Tenant + uploader ownership — for B2B portal evidence (prevents cross-customer media attach). */
    @Transactional(readOnly = true)
    public MediaObject requireUploadedByCurrentUser(UUID mediaId) {
        MediaObject media = requireOwned(mediaId);
        UUID userId = TenantContext.getUserId().orElse(null);
        if (userId == null || media.getCreatedBy() == null || !userId.equals(media.getCreatedBy())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Media not owned by current user");
        }
        return media;
    }

    @Transactional(readOnly = true)
    public InputStream openContent(UUID mediaId) {
        MediaObject media = requireOwned(mediaId);
        return objectStorage.open(media.getStorageKey());
    }

    public String contentPath(UUID mediaId) {
        return "/api/v1/media/" + mediaId + "/content";
    }

    /**
     * Deletes a tenant-owned media object from Postgres and MinIO/S3,
     * cascading catalog rows and avatar references that point at its content URL.
     */
    @Transactional
    public void delete(UUID mediaId) {
        MediaObject media = requireOwned(mediaId);
        UUID tenantId = media.getTenantId();
        String path = contentPath(media.getId());
        String storageKey = media.getStorageKey();

        mediaAttachmentRepository.deleteByTenantIdAndMediaObjectId(tenantId, media.getId());
        productMediaRepository.deleteByTenantIdAndUrl(tenantId, path);

        userRepository.findByTenantIdAndAvatarUrl(tenantId, path).ifPresent(user -> {
            user.setAvatarUrl(null);
            userRepository.save(user);
        });

        mediaObjectRepository.delete(media);
        objectStorage.delete(storageKey);
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (base.length() > 200) {
            base = base.substring(0, 200);
        }
        return base.isBlank() ? null : base.toLowerCase(Locale.ROOT);
    }
}
