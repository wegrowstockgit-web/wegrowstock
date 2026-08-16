package com.invsys.core.tenancy;

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
        assertThat(DataSourceConfig.ensurePrepareThresholdDisabled(null)).isNull();
        assertThat(DataSourceConfig.ensurePrepareThresholdDisabled("")).isEmpty();
    }

    @Test
    void pinsDataPlaneToAppUserAndControlPlaneToAppOwner() {
        assertThat(DataSourceConfig.resolveRuntimeJdbcRole("invsys-api")).isEqualTo("app_user");
        assertThat(DataSourceConfig.resolveRuntimeJdbcRole("")).isEqualTo("app_user");
        assertThat(DataSourceConfig.resolveRuntimeJdbcRole(null)).isEqualTo("app_user");
        assertThat(DataSourceConfig.resolveRuntimeJdbcRole("invsys-admin-api")).isEqualTo("app_owner");
    }
}
