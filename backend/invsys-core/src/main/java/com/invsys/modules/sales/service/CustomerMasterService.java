package com.invsys.modules.sales.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.common.OffsetPaging;
import com.invsys.core.common.PageResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.CustomerCreditLine;
import com.invsys.domain.CustomerPriceTier;
import com.invsys.domain.CustomerUserMapping;
import com.invsys.domain.Invitation;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.repository.CustomerCreditLineRepository;
import com.invsys.modules.sales.repository.CustomerPriceTierRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.CustomerUserMappingRepository;
import com.invsys.repository.InvitationRepository;
import com.invsys.service.CreditService;
import com.invsys.service.UserManagementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CustomerMasterService {

    private static final Set<String> CUSTOMER_SORT = Set.of("name", "createdAt", "email", "customerStatus");

    private final CustomerRepository customerRepository;
    private final CustomerPriceTierRepository priceTierRepository;
    private final CustomerCreditLineRepository creditLineRepository;
    private final CustomerUserMappingRepository mappingRepository;
    private final InvitationRepository invitationRepository;
    private final CreditService creditService;
    private final UserManagementService userManagementService;

    public CustomerMasterService(CustomerRepository customerRepository,
                                 CustomerPriceTierRepository priceTierRepository,
                                 CustomerCreditLineRepository creditLineRepository,
                                 CustomerUserMappingRepository mappingRepository,
                                 InvitationRepository invitationRepository,
                                 CreditService creditService,
                                 UserManagementService userManagementService) {
        this.customerRepository = customerRepository;
        this.priceTierRepository = priceTierRepository;
        this.creditLineRepository = creditLineRepository;
        this.mappingRepository = mappingRepository;
        this.invitationRepository = invitationRepository;
        this.creditService = creditService;
        this.userManagementService = userManagementService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerListItem> list(int page, int size, String search, String sort) {
        UUID tenantId = TenantContext.requireTenantId();
        Page<Customer> result = customerRepository.search(
                tenantId,
                OffsetPaging.keyword(search),
                OffsetPaging.of(page, size, sort, "name", Sort.Direction.ASC, CUSTOMER_SORT));
        return PageResponse.of(result, toItems(tenantId, result.getContent()));
    }

    @Transactional(readOnly = true)
    public CustomerListItem get(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        Customer customer = requireCustomer(id);
        return toItems(tenantId, List.of(customer)).getFirst();
    }

    @Transactional(readOnly = true)
    public CustomerSummary summary() {
        UUID tenantId = TenantContext.requireTenantId();
        return new CustomerSummary(
                customerRepository.countByTenantIdAndCustomerStatus(tenantId, "ACTIVE"),
                customerRepository.countByTenantIdAndCustomerStatus(tenantId, "HOLD"),
                customerRepository.sumCreditLimitByTenantId(tenantId));
    }

    @Transactional
    public List<PriceTierItem> priceTiers(String search) {
        UUID tenantId = TenantContext.requireTenantId();
        List<CustomerPriceTier> rows = priceTierRepository.findByTenantIdOrderByNameAsc(tenantId);
        if (rows.isEmpty()) {
            rows = List.of(
                    seedTier(tenantId, "Wholesale", new BigDecimal("10")),
                    seedTier(tenantId, "VIP", new BigDecimal("15")),
                    seedTier(tenantId, "Tier 1", new BigDecimal("5")));
        }
        String q = search == null ? "" : search.trim().toLowerCase();
        return rows.stream()
                .filter(tier -> q.isBlank() || tier.getName().toLowerCase().contains(q))
                .map(tier -> new PriceTierItem(tier.getId(), tier.getName(), tier.getDiscountPercent()))
                .toList();
    }

    @Transactional
    public void applyCreateExtras(Customer customer,
                                  UUID priceTierId,
                                  String phone,
                                  Boolean taxExempt,
                                  List<Map<String, Object>> shippingAddresses,
                                  Boolean provisionShowroom) {
        if (phone != null && !phone.isBlank()) {
            customer.setPhone(phone.trim());
        }
        if (taxExempt != null) {
            customer.setTaxExempt(taxExempt);
        }
        if (priceTierId != null) {
            priceTierRepository.findById(priceTierId).ifPresent(tier -> customer.setPriceTierId(tier.getId()));
        }
        if (shippingAddresses != null && !shippingAddresses.isEmpty()) {
            applyShippingAddresses(customer, shippingAddresses);
        }
        customerRepository.save(customer);
        if (customer.getCreditLimit() != null) {
            creditService.syncLimit(customer.getId(), customer.getCreditLimit());
        }
        if (Boolean.TRUE.equals(provisionShowroom)) {
            invitePortal(customer.getId());
        }
    }

    @Transactional
    public CustomerListItem invitePortal(UUID customerId) {
        Customer customer = requireCustomer(customerId);
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "Customer email is required to send a B2B portal invite");
        }
        userManagementService.invite(customer.getEmail(), "B2B_CUSTOMER", customer.getId());
        return get(customerId);
    }

    @Transactional
    public CustomerListItem placeOnHold(UUID customerId) {
        Customer customer = requireCustomer(customerId);
        customer.setCustomerStatus("HOLD");
        customerRepository.save(customer);
        creditService.placeOnHold(customer.getId(), customer.getCreditLimit());
        return get(customerId);
    }

    private Customer requireCustomer(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Customer not found"));
        if (!TenantContext.requireTenantId().equals(customer.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Customer not found");
        }
        return customer;
    }

    private CustomerPriceTier seedTier(UUID tenantId, String name, BigDecimal discount) {
        CustomerPriceTier tier = new CustomerPriceTier();
        tier.setTenantId(tenantId);
        tier.setName(name);
        tier.setDiscountPercent(discount);
        return priceTierRepository.save(tier);
    }

    private void applyShippingAddresses(Customer customer, List<Map<String, Object>> shippingAddresses) {
        Map<String, Object> primary = new LinkedHashMap<>(shippingAddresses.getFirst());
        List<Map<String, Object>> extra = new ArrayList<>();
        for (int i = 1; i < shippingAddresses.size(); i++) {
            extra.add(new LinkedHashMap<>(shippingAddresses.get(i)));
        }
        if (!extra.isEmpty()) {
            primary.put("additional", extra);
        }
        customer.setShippingAddress(primary);
    }

    private List<CustomerListItem> toItems(UUID tenantId, List<Customer> customers) {
        if (customers.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = customers.stream().map(Customer::getId).collect(Collectors.toSet());
        Map<UUID, CustomerPriceTier> tiers = priceTierRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(Collectors.toMap(CustomerPriceTier::getId, t -> t, (a, b) -> a));
        Map<UUID, CustomerCreditLine> credits = creditLineRepository.findByTenantIdAndCustomerIdIn(tenantId, ids)
                .stream()
                .collect(Collectors.toMap(CustomerCreditLine::getCustomerId, c -> c, (a, b) -> a));
        Set<UUID> activePortal = mappingRepository.findByCustomerIdIn(ids).stream()
                .map(CustomerUserMapping::getCustomerId)
                .collect(Collectors.toSet());
        Set<UUID> pendingPortal = invitationRepository
                .findByTenantIdAndCustomerIdInAndAcceptedAtIsNull(tenantId, ids).stream()
                .map(Invitation::getCustomerId)
                .collect(Collectors.toSet());
        List<CustomerListItem> items = new ArrayList<>();
        for (Customer customer : customers) {
            CustomerCreditLine credit = credits.get(customer.getId());
            BigDecimal limit = credit != null && credit.getCreditLimit() != null && credit.getCreditLimit().signum() > 0
                    ? credit.getCreditLimit()
                    : (customer.getCreditLimit() != null ? customer.getCreditLimit() : BigDecimal.ZERO);
            BigDecimal available = credit != null && credit.getAvailableCredit() != null
                    ? credit.getAvailableCredit()
                    : limit;
            String tierName = "List";
            if (customer.getPriceTierId() != null && tiers.containsKey(customer.getPriceTierId())) {
                tierName = tiers.get(customer.getPriceTierId()).getName();
            }
            String portal = activePortal.contains(customer.getId())
                    ? "ACTIVE"
                    : pendingPortal.contains(customer.getId()) ? "PENDING" : "NOT_INVITED";
            items.add(new CustomerListItem(
                    customer.getId(),
                    customer.getName(),
                    customer.getEmail(),
                    customer.getPhone(),
                    customer.getCustomerStatus(),
                    customer.getPaymentTerms(),
                    customer.getPriceTierId(),
                    tierName,
                    limit,
                    available,
                    portal,
                    customer.isTaxExempt(),
                    customer.getCurrencyPreference() != null ? customer.getCurrencyPreference() : customer.getDefaultCurrency(),
                    customer.getBillingAddress() != null ? customer.getBillingAddress() : Map.of(),
                    customer.getShippingAddress() != null ? customer.getShippingAddress() : Map.of(),
                    extraShipping(customer.getShippingAddress())));
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extraShipping(Map<String, Object> shipping) {
        if (shipping == null) {
            return List.of();
        }
        Object extra = shipping.get("additional");
        if (extra instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object row : list) {
                if (row instanceof Map<?, ?> map) {
                    out.add(new HashMap<>((Map<String, Object>) map));
                }
            }
            return out;
        }
        return List.of();
    }

    public record CustomerListItem(
            UUID id,
            String name,
            String email,
            String phone,
            String customerStatus,
            String paymentTerms,
            UUID priceTierId,
            String priceTierName,
            BigDecimal creditLimit,
            BigDecimal availableCredit,
            String portalStatus,
            boolean taxExempt,
            String currencyPreference,
            Map<String, Object> billingAddress,
            Map<String, Object> shippingAddress,
            List<Map<String, Object>> shippingAddresses
    ) {
    }

    public record CustomerSummary(long activeB2bAccounts, long accountsOnCreditHold, BigDecimal totalCreditExtended) {
    }

    public record PriceTierItem(UUID id, String name, BigDecimal discountPercent) {
    }
}
