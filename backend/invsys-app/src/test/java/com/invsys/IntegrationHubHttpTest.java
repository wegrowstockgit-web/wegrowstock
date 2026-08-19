package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.LoginRequest;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.domain.ChannelIntegration;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.domain.EdiTradingPartner;
import com.invsys.integration.domain.IntegrationCredential;
import com.invsys.integration.repository.IntegrationCredentialRepository;
import com.invsys.repository.ChannelIntegrationRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.repository.EdiTradingPartnerRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IntegrationHubHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;
    @Autowired IntegrationCredentialRepository credentialRepository;
    @Autowired ChannelIntegrationRepository channelIntegrationRepository;
    @Autowired EdiTradingPartnerRepository ediTradingPartnerRepository;
    @Autowired CustomerRepository customerRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void hubReturnsCatalog_andReflectsConnectedSystems() throws Exception {
        String slug = "hub-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Hub Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        mockMvc.perform(get("/api/v1/integrations/hub")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.categories[0].id").value("ECOMMERCE"))
                .andExpect(jsonPath("$.categories[1].id").value("ACCOUNTING"))
                .andExpect(jsonPath("$.categories[2].id").value("EDI"))
                .andExpect(jsonPath("$.categories[0].integrations[0].id").value("SHOPIFY"))
                .andExpect(jsonPath("$.categories[0].integrations[0].connected").value(false))
                .andExpect(jsonPath("$.categories[0].integrations[1].id").value("AMAZON"))
                .andExpect(jsonPath("$.categories[1].integrations[0].id").value("NETSUITE"))
                .andExpect(jsonPath("$.categories[2].integrations[0].id").value("AS2"));

        TenantContext.setTenantId(tenantId);
        IntegrationCredential shopify = new IntegrationCredential();
        shopify.setTenantId(tenantId);
        shopify.setSystem("SHOPIFY");
        shopify.setStatus("CONNECTED");
        shopify.setCiphertext(new byte[]{1, 2, 3});
        credentialRepository.save(shopify);

        ChannelIntegration amazon = new ChannelIntegration();
        amazon.setTenantId(tenantId);
        amazon.setPlatform("AMAZON");
        amazon.setShopIdentifier("seller-central-1");
        amazon.setStatus("ACTIVE");
        channelIntegrationRepository.save(amazon);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("EDI Hub Customer");
        customer = customerRepository.save(customer);

        EdiTradingPartner partner = new EdiTradingPartner();
        partner.setTenantId(tenantId);
        partner.setCustomerId(customer.getId());
        partner.setAs2Id("AS2-PARTNER-1");
        ediTradingPartnerRepository.save(partner);
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/integrations/hub")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].integrations[0].status").value("LIVE"))
                .andExpect(jsonPath("$.categories[0].integrations[0].connected").value(true))
                .andExpect(jsonPath("$.categories[0].integrations[1].status").value("LIVE"))
                .andExpect(jsonPath("$.categories[2].integrations[0].status").value("LIVE"));
    }

    @Test
    void pickerForbiddenFromHub() throws Exception {
        String slug = "hubb-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Hub Block", slug, "owner@" + slug + ".test", "password123", "Owner"));

        MvcResult inviteMvc = mockMvc.perform(post("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"picker@%s.test","role":"PICKER"}
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(inviteMvc.getResponse().getContentAsString())
                .get("token").asString();

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayName":"Floor Picker","password":"password123"}
                                """.formatted(token)))
                .andExpect(status().isOk());

        TokenResponse picker = authService.login(new LoginRequest(
                "picker@" + slug + ".test", "password123"));

        mockMvc.perform(get("/api/v1/integrations/hub")
                        .header("Authorization", "Bearer " + picker.accessToken()))
                .andExpect(status().isForbidden());
    }
}
