package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.ChannelIntegration;
import com.invsys.repository.ChannelIntegrationRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ChannelIntegrationService {

    private final ChannelIntegrationRepository repository;

    public ChannelIntegrationService(ChannelIntegrationRepository repository) {
        this.repository = repository;
    }

    public List<ChannelIntegration> list() {
        return repository.findByTenantIdOrderByPlatformAsc(TenantContext.requireTenantId());
    }

    @Transactional
    public ChannelIntegration connect(String platform, String shopIdentifier) {
        UUID tenantId = TenantContext.requireTenantId();
        if ("SHOPIFY".equalsIgnoreCase(platform)) {
            shopIdentifier = normalizeShopifyShop(shopIdentifier);
        }
        if (repository.findByPlatformAndShopIdentifier(platform, shopIdentifier).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CONNECTED", "Shop already connected");
        }
        ChannelIntegration integration = new ChannelIntegration();
        integration.setTenantId(tenantId);
        integration.setPlatform(platform);
        integration.setShopIdentifier(shopIdentifier);
        integration.setStatus("ACTIVE");
        return repository.save(integration);
    }

    public static String normalizeShopifyShop(String shopIdentifier) {
        if (shopIdentifier == null || shopIdentifier.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SHOP", "Shopify shop is required");
        }
        String shop = shopIdentifier.trim().toLowerCase();
        shop = shop.replace("https://", "").replace("http://", "");
        int slash = shop.indexOf('/');
        if (slash >= 0) {
            shop = shop.substring(0, slash);
        }
        if (!shop.matches("^[a-z0-9][a-z0-9-]*\\.myshopify\\.com$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SHOP",
                    "Shopify shop must be {store}.myshopify.com");
        }
        return shop;
    }

    @Transactional
    public void disconnect(UUID id) {
        ChannelIntegration integration = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Integration not found"));
        integration.setStatus("DISCONNECTED");
        repository.save(integration);
    }
}
