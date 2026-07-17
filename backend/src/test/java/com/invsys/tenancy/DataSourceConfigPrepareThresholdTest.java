package com.invsys.tenancy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceConfigPrepareThresholdTest {

    @Test
    void appendsPrepareThresholdWhenMissing() {
        assertThat(DataSourceConfig.ensurePrepareThresholdDisabled(
                "jdbc:postgresql://localhost:5432/invsys"))
                .isEqualTo("jdbc:postgresql://localhost:5432/invsys?prepareThreshold=0");
        assertThat(DataSourceConfig.ensurePrepareThresholdDisabled(
                "jdbc:postgresql://localhost:5432/invsys?sslmode=disable"))
                .isEqualTo("jdbc:postgresql://localhost:5432/invsys?sslmode=disable&prepareThreshold=0");
    }

    @Test
    void leavesExistingPrepareThresholdAlone() {
        String url = "jdbc:postgresql://pgbouncer:6432/invsys?prepareThreshold=0";
        assertThat(DataSourceConfig.ensurePrepareThresholdDisabled(url)).isEqualTo(url);
    }
}
