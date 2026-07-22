package com.invsys.integration.inbound;

import com.invsys.core.common.ApiException;
import com.invsys.domain.IntegrationChannel;
import com.invsys.integration.channel.IntegrationChannelType;
import com.invsys.integration.shopify.ShopifyWebhookValidator;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.service.IntegrationChannelService;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Parses Shopify order webhook JSON into {@link CanonicalInboundOrder}.
 * When an active hub channel stores {@code webhookSecret}, verifies {@code X-Shopify-Hmac-Sha256}.
 */
@Component
public class ShopifyOrderAdapter implements ExternalOrderAdapter {

    public static final String HEADER_HMAC = "X-Shopify-Hmac-Sha256";
    /** Set by trusted internal callers (async public-webhook handler) that already verified HMAC. */
    public static final String HEADER_INTERNAL_TRUSTED = "X-InvSys-Internal-Inbound";

    private final ObjectMapper objectMapper;
    private final ProductVariantRepository variantRepository;
    private final IntegrationChannelService channelService;
    private final ShopifyWebhookValidator webhookValidator;

    public ShopifyOrderAdapter(ObjectMapper objectMapper,
                               ProductVariantRepository variantRepository,
                               IntegrationChannelService channelService,
                               ShopifyWebhookValidator webhookValidator) {
        this.objectMapper = objectMapper;
        this.variantRepository = variantRepository;
        this.channelService = channelService;
        this.webhookValidator = webhookValidator;
    }

    @Override
    public boolean supports(String channelType) {
        return channelType != null && "SHOPIFY".equalsIgnoreCase(channelType.trim());
    }

    @Override
    public CanonicalInboundOrder translate(String rawPayload, Map<String, String> headers) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Shopify payload is required");
        }
        verifyWebhookSignature(rawPayload, headers);

        Map<String, Object> root = parseMap(rawPayload);
        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = root.get("order") instanceof Map<?, ?> nested
                ? (Map<String, Object>) nested
                : root;

        String externalRef = firstNonBlank(
                stringVal(orderData.get("name")),
                stringVal(orderData.get("order_number")),
                stringVal(orderData.get("id")),
                "SHOPIFY-" + UUID.randomUUID().toString().substring(0, 8));

        String customerIdentifier = resolveCustomerIdentifier(orderData);
        CanonicalAddress billing = mapAddress(asMap(orderData.get("billing_address")));
        CanonicalAddress shipping = mapAddress(asMap(orderData.get("shipping_address")));
        List<CanonicalOrderLine> lines = mapLines(orderData);

        // Resolve SKUs against local catalog (presence check); keep lines even when missing.
        UUID tenantId = TenantContext.requireTenantId();
        for (CanonicalOrderLine line : lines) {
            if (line.sku() != null && !line.sku().isBlank()) {
                variantRepository.findByTenantIdAndSku(tenantId, line.sku());
            }
        }

        return new CanonicalInboundOrder(
                externalRef,
                ChannelSource.SHOPIFY,
                customerIdentifier,
                billing,
                shipping,
                lines);
    }

    private void verifyWebhookSignature(String rawPayload, Map<String, String> headers) {
        Optional<IntegrationChannel> active = channelService.findActive(IntegrationChannelType.SHOPIFY);
        if (active.isEmpty() || !active.get().hasEncryptedCredentials()) {
            return;
        }
        Map<String, String> secrets = channelService.decryptCredentials(active.get());
        String webhookSecret = firstNonBlank(
                secrets.get("webhookSecret"),
                secrets.get("webhook_secret"),
                secrets.get("sharedSecret"));
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return;
        }
        if (headerValue(headers, HEADER_INTERNAL_TRUSTED) != null) {
            return;
        }
        String hmac = headerValue(headers, HEADER_HMAC);
        if (hmac == null || hmac.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_REQUIRED",
                    "X-Shopify-Hmac-Sha256 is required when a Shopify webhook secret is configured");
        }
        if (!webhookValidator.isValid(rawPayload, hmac, webhookSecret)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID",
                    "Invalid X-Shopify-Hmac-Sha256 signature");
        }
    }

    private List<CanonicalOrderLine> mapLines(Map<String, Object> orderData) {
        Object rawLines = orderData.get("line_items");
        if (!(rawLines instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<CanonicalOrderLine> lines = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) m;
            String sku = stringVal(row.get("sku"));
            if (sku == null || sku.isBlank()) {
                continue;
            }
            BigDecimal qty = toDecimal(row.get("quantity"), BigDecimal.ONE);
            BigDecimal price = toDecimal(row.get("price"), BigDecimal.ZERO);
            lines.add(new CanonicalOrderLine(sku.trim(), qty, price));
        }
        return lines;
    }

    private String resolveCustomerIdentifier(Map<String, Object> orderData) {
        Map<String, Object> customer = asMap(orderData.get("customer"));
        String email = firstNonBlank(
                stringVal(customer.get("email")),
                stringVal(orderData.get("email")),
                stringVal(customer.get("id")));
        return email != null ? email : "shopify-default";
    }

    private CanonicalAddress mapAddress(Map<String, Object> addr) {
        if (addr == null || addr.isEmpty()) {
            return CanonicalAddress.empty();
        }
        return new CanonicalAddress(
                firstNonBlank(stringVal(addr.get("name")),
                        joinName(stringVal(addr.get("first_name")), stringVal(addr.get("last_name")))),
                stringVal(addr.get("address1")),
                stringVal(addr.get("address2")),
                stringVal(addr.get("city")),
                firstNonBlank(stringVal(addr.get("province")), stringVal(addr.get("province_code"))),
                firstNonBlank(stringVal(addr.get("zip")), stringVal(addr.get("postal_code"))),
                firstNonBlank(stringVal(addr.get("country")), stringVal(addr.get("country_code"))));
    }

    private Map<String, Object> parseMap(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Invalid Shopify JSON payload")
                    .withProperty("cause", ex.getMessage());
        }
    }

    private static String headerValue(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty() || name == null) {
            return null;
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && name.equalsIgnoreCase(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }

    private static String joinName(String first, String last) {
        if (first == null && last == null) {
            return null;
        }
        if (first == null) {
            return last;
        }
        if (last == null) {
            return first;
        }
        return (first + " " + last).trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static BigDecimal toDecimal(Object value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
