package com.invsys.health;

import com.invsys.AbstractIntegrationTest;
import com.invsys.config.ActuatorProperties;
import com.invsys.config.ActuatorScrapeAuthorizationManager;
import com.invsys.metrics.WmsMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InfrastructureHealthIndicatorsTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired HikariPoolHealthIndicator hikariPoolHealthIndicator;
    @Autowired RedisPingHealthIndicator redisPingHealthIndicator;
    @Autowired S3WriteAccessHealthIndicator s3WriteAccessHealthIndicator;
    @Autowired ActuatorScrapeAuthorizationManager scrapeAuthorizationManager;
    @Autowired WmsMetrics wmsMetrics;
    @Autowired MeterRegistry meterRegistry;

    @Test
    void hikariHealthIsUpWithPoolDetails() {
        Health health = hikariPoolHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKeys("active", "idle", "maximumPoolSize");
    }

    @Test
    void redisHealthReportsDisabledInTestProfile() {
        Health health = redisPingHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("enabled", false);
    }

    @Test
    void s3WriteHealthProbesBucket() {
        Health health = s3WriteAccessHealthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("bucket");
    }

    @Test
    void actuatorHealthExposesCustomComponents() throws Exception {
        mockMvc.perform(get("/actuator/health").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void prometheusExposesWmsMeters() throws Exception {
        wmsMetrics.incrementOrdersProcessed();
        wmsMetrics.recordAllocation(12_000_000L);
        wmsMetrics.incrementApiError("POST /api/v1/sales-orders");

        mockMvc.perform(get("/actuator/prometheus").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).contains("wms_orders_processed_total");
                    assertThat(body).contains("wms_allocation_time_seconds");
                    assertThat(body).contains("wms_api_errors_total");
                });
    }

    @Test
    void scrapeAuthorizationAllowsPrivateCidrAndRejectsPublic() {
        var anon = new AnonymousAuthenticationToken(
                "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        ActuatorProperties vpcOnly = new ActuatorProperties();
        vpcOnly.setScrapeAllowedCidrs("10.0.0.0/8,172.16.0.0/12");
        ActuatorScrapeAuthorizationManager vpc = new ActuatorScrapeAuthorizationManager(
                vpcOnly, new com.invsys.core.security.ClientIpResolver(vpcOnly, ""));

        MockHttpServletRequest privateReq = new MockHttpServletRequest();
        privateReq.setRemoteAddr("10.0.4.20");
        assertThat(vpc.authorize(() -> anon, new RequestAuthorizationContext(privateReq)).isGranted())
                .isTrue();

        MockHttpServletRequest loopbackReq = new MockHttpServletRequest();
        loopbackReq.setRemoteAddr("127.0.0.1");
        assertThat(scrapeAuthorizationManager.authorize(() -> anon, new RequestAuthorizationContext(loopbackReq)).isGranted())
                .isTrue();

        MockHttpServletRequest publicReq = new MockHttpServletRequest();
        publicReq.setRemoteAddr("8.8.8.8");
        assertThat(vpc.authorize(() -> anon, new RequestAuthorizationContext(publicReq)).isGranted())
                .isFalse();
    }

    @Test
    void wmsMetricsRegistersOnSimpleRegistry() {
        SimpleMeterRegistry simple = new SimpleMeterRegistry();
        WmsMetrics metrics = new WmsMetrics(simple);
        var sample = metrics.startAllocation();
        metrics.stopAllocation(sample);
        metrics.incrementOrdersProcessed();
        metrics.incrementApiError("GET /api/v1/demo");
        assertThat(simple.find(WmsMetrics.ORDERS_PROCESSED).counter()).isNotNull();
        assertThat(simple.find(WmsMetrics.ALLOCATION_TIME).timer()).isNotNull();
        assertThat(simple.find(WmsMetrics.API_ERRORS).tag("endpoint", "GET /api/v1/demo").counter().count())
                .isEqualTo(1.0);
    }
}
