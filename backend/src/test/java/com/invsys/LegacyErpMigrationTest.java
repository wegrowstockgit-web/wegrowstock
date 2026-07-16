package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.InventoryLedger;
import com.invsys.domain.Location;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LegacyErpMigrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired LocationRepository locationRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired InventoryLedgerRepository ledgerRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void legacyMigrationCreatesVariantsAndInitialMigrationReceivesAtomically() throws Exception {
        String slug = "mig-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Mig Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-MIG");
        wh.setName("Mig WH");
        wh.setPath("/WH-MIG");
        wh = locationRepository.save(wh);
        TenantContext.clear();

        String csv = "sku,name,barcode,qty,unitCost\nMIG-A,Widget A,111,5,3.00\nMIG-B,Widget B,222,2,4.50\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "items.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        String mapping = """
                {"sku":"sku","name":"name","barcode":"barcode","qty":"qty","unitCost":"unitCost"}
                """;

        mockMvc.perform(multipart("/api/v1/ingestion/legacy-migration")
                        .file(file)
                        .param("columnsMapping", mapping)
                        .param("locationId", wh.getId().toString())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2));

        TenantContext.setTenantId(tenantId);
        ProductVariant a = variantRepository.findByTenantIdAndSku(tenantId, "MIG-A").orElseThrow();
        List<InventoryLedger> ledger = ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtDesc(tenantId, a.getId());
        assertThat(ledger).isNotEmpty();
        assertThat(ledger.getFirst().getMovementType()).isEqualTo("RECEIVE");
        assertThat(ledger.getFirst().getReasonCode()).isEqualTo("INITIAL_MIGRATION");
    }
}
