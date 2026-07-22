package com.invsys.core.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcSupportContextPropagationTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void wrapWithContextPropagatesMdcOntoVirtualThread() throws Exception {
        UUID tenant = UUID.randomUUID();
        MDC.put(MdcSupport.TENANT_ID, tenant.toString());
        MDC.put(MdcSupport.REQUEST_ID, "parent-req");
        MDC.put(MdcSupport.USER_ID, UUID.randomUUID().toString());

        AtomicReference<String> childTenant = new AtomicReference<>();
        AtomicReference<String> childRequest = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        try (ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor()) {
            vt.execute(MdcSupport.wrapWithContext(() -> {
                childTenant.set(MDC.get(MdcSupport.TENANT_ID));
                childRequest.set(MDC.get(MdcSupport.REQUEST_ID));
                done.countDown();
            }));
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(childTenant.get()).isEqualTo(tenant.toString());
        assertThat(childRequest.get()).isEqualTo("parent-req");
    }

    @Test
    void runRestoresPreviousMdc() {
        MDC.put(MdcSupport.REQUEST_ID, "outer");
        MdcSupport.run(UUID.randomUUID(), "inner", null, () -> {
            assertThat(MDC.get(MdcSupport.REQUEST_ID)).isEqualTo("inner");
            return null;
        });
        assertThat(MDC.get(MdcSupport.REQUEST_ID)).isEqualTo("outer");
    }
}
