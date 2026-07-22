package com.invsys.integration.shopify;

import com.invsys.domain.ChannelIntegration;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.domain.MediaObject;
import com.invsys.core.integration.CredentialVaultService;
import com.invsys.integration.repository.IntegrationCredentialRepository;
import com.invsys.media.ObjectStorage;
import com.invsys.repository.ChannelIntegrationRepository;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.repository.MediaObjectRepository;
import com.invsys.core.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shopify GraphQL Admin media pipeline:
 * 1) stagedUploadsCreate → 2) multipart POST to staged target → 3) fileCreate + productVariantAppendMedia.
 */
@Service
public class ShopifyMediaSyncService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyMediaSyncService.class);
    private static final String API_VERSION = "2024-10";

    private final ChannelIntegrationRepository channelIntegrationRepository;
    private final IntegrationCredentialRepository credentialRepository;
    private final CredentialVaultService credentialVaultService;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final MediaObjectRepository mediaObjectRepository;
    private final ObjectStorage objectStorage;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ShopifyMediaSyncService(ChannelIntegrationRepository channelIntegrationRepository,
                                   IntegrationCredentialRepository credentialRepository,
                                   CredentialVaultService credentialVaultService,
                                   IntegrationSyncLogRepository syncLogRepository,
                                   MediaObjectRepository mediaObjectRepository,
                                   ObjectStorage objectStorage) {
        this.channelIntegrationRepository = channelIntegrationRepository;
        this.credentialRepository = credentialRepository;
        this.credentialVaultService = credentialVaultService;
        this.syncLogRepository = syncLogRepository;
        this.mediaObjectRepository = mediaObjectRepository;
        this.objectStorage = objectStorage;
    }

    public IntegrationSyncLog syncVariantMedia(UUID tenantId, UUID variantId, Map<String, Object> payload) {
        TenantContext.setTenantId(tenantId);
        IntegrationSyncLog syncLog = new IntegrationSyncLog();
        syncLog.setTenantId(tenantId);
        syncLog.setSystem("SHOPIFY");
        syncLog.setEntityType("VARIANT_MEDIA");
        syncLog.setEntityId(variantId);

        Optional<ChannelIntegration> integration = channelIntegrationRepository
                .findByTenantIdOrderByPlatformAsc(tenantId).stream()
                .filter(ci -> "SHOPIFY".equalsIgnoreCase(ci.getPlatform()))
                .filter(ci -> "ACTIVE".equals(ci.getStatus()))
                .findFirst();

        if (integration.isEmpty() || integration.get().getCredentialId() == null) {
            syncLog.setStatus("SKIPPED");
            syncLog.setLastError("No Shopify integration configured");
            return syncLogRepository.save(syncLog);
        }

        String mediaUrl = stringVal(payload.get("url"));
        if (mediaUrl == null || mediaUrl.isBlank()) {
            syncLog.setStatus("SKIPPED");
            syncLog.setLastError("Missing media url in payload");
            return syncLogRepository.save(syncLog);
        }

        try {
            String accessToken = resolveAccessToken(integration.get().getCredentialId());
            String shop = integration.get().getShopIdentifier();
            String graphqlUrl = "https://" + shop + "/admin/api/" + API_VERSION + "/graphql.json";

            byte[] bytes = loadMediaBytes(tenantId, mediaUrl);
            String filename = filenameFromUrl(mediaUrl);
            String mimeType = guessMime(filename);

            // Step 1: stagedUploadsCreate
            String stagedMutation = """
                    mutation stagedUploadsCreate($input: [StagedUploadInput!]!) {
                      stagedUploadsCreate(input: $input) {
                        stagedTargets {
                          url
                          resourceUrl
                          parameters { name value }
                        }
                        userErrors { field message }
                      }
                    }
                    """;
            String stagedBody = """
                    {
                      "query": %s,
                      "variables": {
                        "input": [{
                          "filename": "%s",
                          "mimeType": "%s",
                          "httpMethod": "POST",
                          "resource": "FILE",
                          "fileSize": "%d"
                        }]
                      }
                    }
                    """.formatted(jsonString(stagedMutation), escape(filename), escape(mimeType), bytes.length);

            String stagedResponse = postGraphql(graphqlUrl, accessToken, stagedBody);
            String uploadUrl = extractJsonString(stagedResponse, "url");
            String resourceUrl = extractJsonString(stagedResponse, "resourceUrl");
            if (uploadUrl == null || resourceUrl == null) {
                syncLog.setStatus("FAILED");
                syncLog.setLastError("stagedUploadsCreate missing target: " + truncate(stagedResponse));
                return syncLogRepository.save(syncLog);
            }

            // Step 2: multipart POST to staged bucket (parameters + file)
            String boundary = "----InvSysShopify" + UUID.randomUUID().toString().replace("-", "");
            byte[] multipart = buildMultipart(boundary, stagedResponse, filename, mimeType, bytes);
            HttpResponse<String> uploadResponse = httpClient.send(
                    HttpRequest.newBuilder(URI.create(uploadUrl))
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(HttpRequest.BodyPublishers.ofByteArray(multipart))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (uploadResponse.statusCode() >= 300) {
                syncLog.setStatus("FAILED");
                syncLog.setLastError("Staged upload HTTP " + uploadResponse.statusCode());
                return syncLogRepository.save(syncLog);
            }

            // Step 3: fileCreate + productVariantAppendMedia
            String shopifyVariantGid = stringVal(payload.get("shopifyVariantGid"));
            if (shopifyVariantGid == null || shopifyVariantGid.isBlank()) {
                shopifyVariantGid = "gid://shopify/ProductVariant/" + variantId;
            }
            String bindMutation = """
                    mutation fileCreateAndAppend($files: [FileCreateInput!]!, $variantId: ID!, $media: [CreateMediaInput!]!) {
                      fileCreate(files: $files) {
                        files { ... on MediaImage { id } }
                        userErrors { field message }
                      }
                      productVariantAppendMedia(variantId: $variantId, media: $media) {
                        productVariant { id }
                        userErrors { field message }
                      }
                    }
                    """;
            String bindBody = """
                    {
                      "query": %s,
                      "variables": {
                        "files": [{ "originalSource": "%s", "contentType": "IMAGE" }],
                        "variantId": "%s",
                        "media": [{ "originalSource": "%s", "mediaContentType": "IMAGE" }]
                      }
                    }
                    """.formatted(jsonString(bindMutation), escape(resourceUrl), escape(shopifyVariantGid),
                    escape(resourceUrl));

            String bindResponse = postGraphql(graphqlUrl, accessToken, bindBody);
            boolean failed = bindResponse.contains("\"userErrors\":[{")
                    && !bindResponse.contains("\"userErrors\":[]");
            syncLog.setStatus(failed ? "FAILED" : "SYNCED");
            if (failed) {
                syncLog.setLastError(truncate(bindResponse));
            }
            log.info("Shopify media sync tenant={} variant={} status={}", tenantId, variantId, syncLog.getStatus());
            return syncLogRepository.save(syncLog);
        } catch (Exception ex) {
            syncLog.setStatus("FAILED");
            syncLog.setLastError(ex.getMessage());
            log.warn("Shopify media sync failed tenant={} variant={}: {}", tenantId, variantId, ex.getMessage());
            return syncLogRepository.save(syncLog);
        }
    }

    private byte[] loadMediaBytes(UUID tenantId, String mediaUrl) throws Exception {
        Optional<UUID> mediaId = extractFirstPartyMediaId(mediaUrl);
        if (mediaId.isPresent()) {
            MediaObject media = mediaObjectRepository.findByTenantIdAndId(tenantId, mediaId.get())
                    .orElseThrow(() -> new IllegalStateException("Media object not found: " + mediaId.get()));
            try (InputStream in = objectStorage.open(media.getStorageKey())) {
                return in.readAllBytes();
            }
        }
        URI uri = URI.create(mediaUrl);
        if (!uri.isAbsolute()) {
            throw new IllegalStateException("Cannot fetch relative media URL without first-party media id: " + mediaUrl);
        }
        return httpClient.send(
                HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).body();
    }

    private static Optional<UUID> extractFirstPartyMediaId(String mediaUrl) {
        if (mediaUrl == null) {
            return Optional.empty();
        }
        // Matches /api/v1/media/{uuid}/content (absolute or relative)
        int marker = mediaUrl.indexOf("/api/v1/media/");
        if (marker < 0) {
            return Optional.empty();
        }
        String rest = mediaUrl.substring(marker + "/api/v1/media/".length());
        int slash = rest.indexOf('/');
        String idPart = slash >= 0 ? rest.substring(0, slash) : rest;
        try {
            return Optional.of(UUID.fromString(idPart));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String postGraphql(String url, String accessToken, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-Shopify-Access-Token", accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private String resolveAccessToken(UUID credentialId) {
        var credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new IllegalStateException("Shopify credential missing"));
        String token = new String(credentialVaultService.decrypt(credential.getCiphertext()), StandardCharsets.UTF_8);
        if (token.isBlank()) {
            throw new IllegalStateException("Shopify access token missing");
        }
        return token;
    }

    private static byte[] buildMultipart(String boundary, String stagedJson, String filename,
                                         String mimeType, byte[] fileBytes) {
        StringBuilder form = new StringBuilder();
        // Include parameter pairs returned by stagedUploadsCreate when present
        int idx = 0;
        while (true) {
            String name = extractNthJsonField(stagedJson, "name", idx);
            String value = extractNthJsonField(stagedJson, "value", idx);
            if (name == null || value == null) {
                break;
            }
            form.append("--").append(boundary).append("\r\n");
            form.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
            form.append(value).append("\r\n");
            idx++;
        }
        form.append("--").append(boundary).append("\r\n");
        form.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(filename).append("\"\r\n");
        form.append("Content-Type: ").append(mimeType).append("\r\n\r\n");
        byte[] head = form.toString().getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[head.length + fileBytes.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(fileBytes, 0, out, head.length, fileBytes.length);
        System.arraycopy(tail, 0, out, head.length + fileBytes.length, tail.length);
        return out;
    }

    private static String extractJsonString(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        start += needle.length();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : null;
    }

    private static String extractNthJsonField(String json, String field, int n) {
        String needle = "\"" + field + "\":\"";
        int from = 0;
        for (int i = 0; i <= n; i++) {
            int start = json.indexOf(needle, from);
            if (start < 0) {
                return null;
            }
            start += needle.length();
            int end = json.indexOf('"', start);
            if (end < 0) {
                return null;
            }
            if (i == n) {
                return json.substring(start, end);
            }
            from = end + 1;
        }
        return null;
    }

    private static String filenameFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path == null || path.isBlank()) {
                return "variant-media.jpg";
            }
            String name = path.substring(path.lastIndexOf('/') + 1);
            return name.isBlank() ? "variant-media.jpg" : name;
        } catch (Exception ex) {
            return "variant-media.jpg";
        }
    }

    private static String guessMime(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jsonString(String value) {
        return "\"" + escape(value.replace("\n", "\\n")) + "\"";
    }

    private static String truncate(String value) {
        return value == null ? null : (value.length() > 500 ? value.substring(0, 500) : value);
    }
}
