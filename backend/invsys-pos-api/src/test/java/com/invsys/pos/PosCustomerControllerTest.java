package com.invsys.pos;

import com.invsys.core.tenancy.TenantContext;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PosCustomerControllerTest {

    @Mock CustomerRepository customerRepository;

    private MockMvc mockMvc;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("a0000000-0000-4000-8000-000000000001");
        TenantContext.setTenantId(tenantId);
        mockMvc = MockMvcBuilders.standaloneSetup(new PosCustomerController(customerRepository))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void customers_returnsCrmProfilesForTheRegister() throws Exception {
        Customer customer = new Customer();
        customer.setId(UUID.fromString("a0000000-0000-4000-8000-000000001101"));
        customer.setName("Retail Partners LLC");
        customer.setEmail("ap@retailpartners.com");
        when(customerRepository.findByTenantIdOrderByNameAsc(tenantId)).thenReturn(List.of(customer));

        mockMvc.perform(get("/api/v1/pos/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Retail Partners LLC"))
                .andExpect(jsonPath("$[0].email").value("ap@retailpartners.com"));
    }
}
