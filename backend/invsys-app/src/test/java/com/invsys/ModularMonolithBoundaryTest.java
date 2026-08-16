package com.invsys;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * CI guardrail: a new cross-module Service/Repository import fails the build.
 *
 * <p>Detection uses {@link com.invsys.modules.BoundedContextDetectionStrategy} so
 * {@code ApplicationModules.of(InvSysApplication.class)} only sees
 * {@code com.invsys.modules.*}. {@link com.invsys.architecture.ModularMonolithBoundaryTest}
 * runs the same check via {@code ApplicationModules.of(InvSysApplication.class)}.
 */
class ModularMonolithBoundaryTest {

    @Test
    void modulesVerify() {
        ApplicationModules modules = ApplicationModules.of("com.invsys.modules");
        modules.verify();
    }
}
