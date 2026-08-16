package com.invsys;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * CI guardrail: a new cross-module Service/Repository import fails the build.
 *
 * <p>{@link InvSysApplication} lives in {@code com.invsys}, so
 * {@code ApplicationModules.of(InvSysApplication.class)} would treat every
 * sibling package ({@code api}, {@code service}, …) as a module. Bounded
 * contexts live under {@code com.invsys.modules} and are verified here.
 */
class ModularMonolithBoundaryTest {

    @Test
    void modulesVerify() {
        ApplicationModules modules = ApplicationModules.of("com.invsys.modules");
        modules.verify();
    }
}
