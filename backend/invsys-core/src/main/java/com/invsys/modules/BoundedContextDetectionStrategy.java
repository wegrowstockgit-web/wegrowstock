package com.invsys.modules;

import org.springframework.modulith.core.ApplicationModuleDetectionStrategy;
import org.springframework.modulith.core.JavaPackage;

import java.util.stream.Stream;

/**
 * Bounded contexts live under {@code com.invsys.modules.*}, while the boot class is
 * {@code com.invsys.InvSysApplication}. Direct-subpackage detection from the boot
 * package would treat {@code api}, {@code service}, and {@code config} as modules.
 *
 * <p>Used with {@code @Modulith(additionalPackages = "com.invsys.modules")}: the
 * {@code com.invsys} root contributes no modules; the {@code modules} root contributes
 * {@code catalog}, {@code inventory}, {@code sales}, {@code fulfillment}, {@code fintech},
 * and {@code purchasing}.
 */
public final class BoundedContextDetectionStrategy implements ApplicationModuleDetectionStrategy {

    @Override
    public Stream<JavaPackage> getModuleBasePackages(JavaPackage basePackage) {
        if (isModulesRoot(basePackage)) {
            return basePackage.getDirectSubPackages().stream();
        }
        return Stream.empty();
    }

    static boolean isModulesRoot(JavaPackage basePackage) {
        return isModulesRootName(basePackage.getName());
    }

    static boolean isModulesRootName(String name) {
        return name != null && name.endsWith(".modules") && !name.contains(".modules.");
    }
}
