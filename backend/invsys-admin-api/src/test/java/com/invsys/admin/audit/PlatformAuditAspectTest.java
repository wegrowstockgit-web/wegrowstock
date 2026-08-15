package com.invsys.admin.audit;

import com.invsys.admin.audit.PlatformAudit;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAuditAspectTest {

    @Test
    void annotationExposesActionAndTenantParam() throws Exception {
        Method method = Sample.class.getDeclaredMethod("mutate", java.util.UUID.class);
        PlatformAudit audit = method.getAnnotation(PlatformAudit.class);
        assertThat(audit).isNotNull();
        assertThat(audit.action()).isEqualTo("SAMPLE_ACTION");
        assertThat(audit.tenantIdParam()).isEqualTo("tenantId");
    }

    static class Sample {
        @PlatformAudit(action = "SAMPLE_ACTION", tenantIdParam = "tenantId")
        void mutate(java.util.UUID tenantId) {
        }
    }
}
