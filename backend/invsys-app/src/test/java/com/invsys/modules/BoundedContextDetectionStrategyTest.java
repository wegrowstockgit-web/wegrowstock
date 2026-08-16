package com.invsys.modules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedContextDetectionStrategyTest {

    @Test
    void recognizesModulesRootPackageName() {
        assertThat(BoundedContextDetectionStrategy.isModulesRootName("com.invsys.modules")).isTrue();
        assertThat(BoundedContextDetectionStrategy.isModulesRootName("com.invsys")).isFalse();
        assertThat(BoundedContextDetectionStrategy.isModulesRootName("com.invsys.modules.inventory")).isFalse();
        assertThat(BoundedContextDetectionStrategy.isModulesRootName(null)).isFalse();
    }
}
