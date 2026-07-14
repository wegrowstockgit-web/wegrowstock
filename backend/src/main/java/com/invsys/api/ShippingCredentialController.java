package com.invsys.api;

import com.invsys.service.ShippingCredentialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/shipping-accounts")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class ShippingCredentialController {

    private final ShippingCredentialService shippingCredentialService;

    public ShippingCredentialController(ShippingCredentialService shippingCredentialService) {
        this.shippingCredentialService = shippingCredentialService;
    }

    @GetMapping
    public List<ShippingCredentialService.ShippingCredentialStatus> list() {
        return shippingCredentialService.list();
    }

    @PostMapping
    public ShippingCredentialService.ShippingCredentialStatus save(@Valid @RequestBody SaveCredentialRequest request) {
        return shippingCredentialService.save(request.system(), request.apiKey());
    }

    public record SaveCredentialRequest(@NotBlank String system, @NotBlank String apiKey) {
    }
}
