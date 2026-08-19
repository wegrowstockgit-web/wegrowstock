package com.invsys.api;

import com.invsys.core.common.ApiException;
import com.invsys.domain.MediaAttachment;
import com.invsys.domain.MediaObject;
import com.invsys.domain.TransactionMedia;
import com.invsys.media.MediaAttachmentService;
import com.invsys.media.MediaCompleteService;
import com.invsys.media.MediaPreSignService;
import com.invsys.media.MediaUploadService;
import com.invsys.service.TransactionMediaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@PreAuthorize("isAuthenticated()")
public class MediaController {

    private final MediaUploadService uploadService;
    private final MediaAttachmentService attachmentService;
    private final MediaPreSignService preSignService;
    private final MediaCompleteService completeService;
    private final TransactionMediaService transactionMediaService;

    public MediaController(MediaUploadService uploadService,
                           MediaAttachmentService attachmentService,
                           MediaPreSignService preSignService,
                           MediaCompleteService completeService,
                           TransactionMediaService transactionMediaService) {
        this.uploadService = uploadService;
        this.attachmentService = attachmentService;
        this.preSignService = preSignService;
        this.completeService = completeService;
        this.transactionMediaService = transactionMediaService;
    }

    @GetMapping("/presign-upload")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER','B2B_CUSTOMER')")
    public PresignResponse presignUpload(@RequestParam String type,
                                         @RequestParam String filename,
                                         @RequestParam String contentType) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!"USER_AVATAR".equals(normalizedType)
                && !"TRANSACTION".equals(normalizedType)
                && !hasOpsRole()
                && !hasB2bRole()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Only operations roles may pre-sign non-avatar media");
        }
        MediaPreSignService.PresignUploadResult result = preSignService.presignUpload(type, filename, contentType);
        return new PresignResponse(
                result.objectKey(),
                result.uploadUrl(),
                result.contentType(),
                result.type(),
                result.expiresInSeconds());
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER','B2B_CUSTOMER')")
    public MediaObjectResponse complete(@Valid @RequestBody CompleteRequest request) {
        MediaObject media = completeService.complete(request.objectKey(), request.contentType());
        return toObjectResponse(media);
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER','B2B_CUSTOMER')")
    public MediaObjectResponse upload(@RequestPart("file") MultipartFile file,
                                      @RequestParam(defaultValue = "EVIDENCE") String kind) {
        MediaUploadService.UploadKind uploadKind = parseKind(kind);
        // B2B portal self-serve RMA evidence; avatars for all roles; ops for other kinds
        boolean portalEvidence = uploadKind == MediaUploadService.UploadKind.EVIDENCE && hasB2bRole();
        if (uploadKind != MediaUploadService.UploadKind.AVATAR && !portalEvidence && !hasOpsRole()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Only operations roles may upload non-avatar media");
        }
        MediaObject media = uploadService.upload(file, uploadKind);
        return toObjectResponse(media);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable UUID id) {
        // B2B portal users may only fetch media they uploaded (RMA evidence isolation)
        MediaObject media = hasB2bRole() && !hasOpsRole()
                ? uploadService.requireUploadedByCurrentUser(id)
                : uploadService.requireOwned(id);
        InputStream stream = uploadService.openContent(id);
        String contentType = media.getContentType() == null ? "application/octet-stream" : media.getContentType();
        boolean activeContent = contentType.toLowerCase().contains("svg")
                || contentType.toLowerCase().contains("html")
                || contentType.toLowerCase().contains("xml");
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .header(HttpHeaders.CONTENT_DISPOSITION, activeContent ? "attachment" : "inline")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(media.getByteSize())
                .body(new InputStreamResource(stream));
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public void delete(@PathVariable UUID mediaId) {
        uploadService.delete(mediaId);
    }

    @PostMapping("/attachments")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public MediaAttachmentResponse attach(@Valid @RequestBody AttachRequest request) {
        MediaAttachment attachment = attachmentService.attach(
                request.mediaObjectId(),
                request.entityType(),
                request.entityId(),
                request.purpose(),
                request.sortOrder());
        return toAttachmentResponse(attachment);
    }

    @GetMapping("/attachments")
    public List<MediaAttachmentResponse> list(@RequestParam String entityType,
                                              @RequestParam UUID entityId) {
        return attachmentService.list(entityType, entityId).stream()
                .map(this::toAttachmentResponse)
                .toList();
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public TransactionMediaResponse attachTransaction(@Valid @RequestBody TransactionMediaRequest request) {
        TransactionMedia media = transactionMediaService.attach(
                request.entityType(), request.entityId(), request.url());
        return new TransactionMediaResponse(
                media.getId(), media.getEntityType(), media.getEntityId(),
                media.getUrl(), media.getCapturedBy(), media.getCreatedAt());
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<TransactionMediaResponse> listTransactions(@RequestParam String entityType,
                                                           @RequestParam UUID entityId) {
        return transactionMediaService.list(entityType, entityId).stream()
                .map(m -> new TransactionMediaResponse(
                        m.getId(), m.getEntityType(), m.getEntityId(),
                        m.getUrl(), m.getCapturedBy(), m.getCreatedAt()))
                .toList();
    }

    private MediaUploadService.UploadKind parseKind(String kind) {
        try {
            return MediaUploadService.UploadKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return MediaUploadService.UploadKind.EVIDENCE;
        }
    }

    private static boolean hasOpsRole() {
        return hasRole("ROLE_OWNER", "ROLE_ADMIN", "ROLE_WAREHOUSE_MANAGER", "ROLE_PICKER");
    }

    private static boolean hasB2bRole() {
        return hasRole("ROLE_B2B_CUSTOMER");
    }

    private static boolean hasRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String role = authority.getAuthority();
            for (String allowed : roles) {
                if (allowed.equals(role)) {
                    return true;
                }
            }
        }
        return false;
    }

    private MediaObjectResponse toObjectResponse(MediaObject media) {
        return new MediaObjectResponse(
                media.getId(),
                uploadService.contentPath(media.getId()),
                media.getContentType(),
                media.getByteSize(),
                media.getOriginalFilename(),
                media.getChecksumSha256());
    }

    private MediaAttachmentResponse toAttachmentResponse(MediaAttachment attachment) {
        return new MediaAttachmentResponse(
                attachment.getId(),
                attachment.getMediaObjectId(),
                uploadService.contentPath(attachment.getMediaObjectId()),
                attachment.getEntityType(),
                attachment.getEntityId(),
                attachment.getPurpose(),
                attachment.getSortOrder());
    }

    public record PresignResponse(
            String objectKey,
            String uploadUrl,
            String contentType,
            String type,
            long expiresInSeconds
    ) {
    }

    public record CompleteRequest(
            @NotBlank String objectKey,
            String contentType
    ) {
    }

    public record MediaObjectResponse(
            UUID id,
            String contentUrl,
            String contentType,
            long byteSize,
            String originalFilename,
            String checksumSha256
    ) {
    }

    public record AttachRequest(
            @NotNull UUID mediaObjectId,
            @NotBlank String entityType,
            @NotNull UUID entityId,
            @NotBlank String purpose,
            Integer sortOrder
    ) {
    }

    public record MediaAttachmentResponse(
            UUID id,
            UUID mediaObjectId,
            String contentUrl,
            String entityType,
            UUID entityId,
            String purpose,
            int sortOrder
    ) {
    }

    public record TransactionMediaRequest(
            @NotBlank String entityType,
            @NotNull UUID entityId,
            @NotBlank String url
    ) {
    }

    public record TransactionMediaResponse(
            UUID id,
            String entityType,
            UUID entityId,
            String url,
            UUID capturedBy,
            java.time.Instant createdAt
    ) {
    }
}
