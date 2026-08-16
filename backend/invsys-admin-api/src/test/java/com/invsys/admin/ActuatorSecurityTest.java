package com.invsys.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ActuatorSecurityTest extends AbstractAdminIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void prometheusFromUntrustedIpIsForbidden() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(request -> {
                    request.setRemoteAddr("8.8.8.8");
                    return request;
                }))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthFromUntrustedIpIsForbidden() throws Exception {
        mockMvc.perform(get("/actuator/health").with(request -> {
                    request.setRemoteAddr("203.0.113.10");
                    return request;
                }))
                .andExpect(status().isForbidden());
    }

    @Test
    void prometheusFromLoopbackIsAllowed() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk());
    }
}
