package com.invsys.api;

import com.invsys.api.dto.AccountMappingResponse;
import com.invsys.domain.AccountMapping;
import com.invsys.integration.accounting.LedgerAccount;
import com.invsys.service.AccountMappingService;
import com.invsys.service.AccountingChartOfAccountsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/accounting")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class AccountMappingController {

    private final AccountMappingService accountMappingService;
    private final AccountingChartOfAccountsService chartOfAccountsService;

    public AccountMappingController(AccountMappingService accountMappingService,
                                    AccountingChartOfAccountsService chartOfAccountsService) {
        this.accountMappingService = accountMappingService;
        this.chartOfAccountsService = chartOfAccountsService;
    }

    @GetMapping("/accounts")
    public List<LedgerAccount> listProviderAccounts(@RequestParam String provider) {
        return chartOfAccountsService.listAccounts(provider);
    }

    @PostMapping("/accounts/auto-provision")
    public List<LedgerAccount> autoProvision(@Valid @RequestBody AutoProvisionRequest request) {
        return chartOfAccountsService.autoProvision(request.provider());
    }

    @GetMapping("/mappings")
    public List<AccountMappingResponse> list(@RequestParam(required = false) String system) {
        return accountMappingService.list(system).stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping("/mappings")
    public AccountMappingResponse upsert(@Valid @RequestBody UpsertMappingRequest request) {
        return toResponse(accountMappingService.upsert(
                request.system(), request.accountType(), request.externalAccountId()));
    }

    @PutMapping("/mappings/bulk")
    public List<AccountMappingResponse> upsertBulk(@Valid @RequestBody BulkUpsertRequest request) {
        List<AccountMappingService.UpsertInput> inputs = request.mappings().stream()
                .map(m -> new AccountMappingService.UpsertInput(m.system(), m.accountType(), m.externalAccountId()))
                .toList();
        return accountMappingService.upsertAll(inputs).stream()
                .map(this::toResponse)
                .toList();
    }

    @DeleteMapping("/mappings/{id}")
    public void delete(@PathVariable UUID id) {
        accountMappingService.delete(id);
    }

    private AccountMappingResponse toResponse(AccountMapping mapping) {
        return new AccountMappingResponse(
                mapping.getId(),
                mapping.getSystem(),
                mapping.getAccountType(),
                mapping.getExternalAccountId());
    }

    public record UpsertMappingRequest(
            @NotBlank String system,
            @NotBlank String accountType,
            @NotBlank String externalAccountId
    ) {
    }

    public record BulkUpsertRequest(@NotEmpty List<UpsertMappingRequest> mappings) {
    }

    public record AutoProvisionRequest(@NotBlank String provider) {
    }
}
