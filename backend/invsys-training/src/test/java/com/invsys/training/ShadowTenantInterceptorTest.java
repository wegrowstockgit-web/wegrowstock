package com.invsys.training;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ShadowTenantInterceptorTest {

    @Test
    void detectsTrainingModeHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(ShadowTenantInterceptor.isTrainingRequest(request)).isFalse();
        request.addHeader(ShadowTenantInterceptor.HEADER_TRAINING_MODE, "true");
        assertThat(ShadowTenantInterceptor.isTrainingRequest(request)).isTrue();
    }
}
