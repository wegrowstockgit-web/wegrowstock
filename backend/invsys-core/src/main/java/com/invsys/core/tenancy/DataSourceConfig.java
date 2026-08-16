package com.invsys.core.tenancy;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    /**
     * Hikari settings ({@code spring.datasource.hikari.*}) bind onto this bean before it is
     * wrapped for tenant GUC injection.
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource hikariDataSource(
            DataSourceProperties properties,
            @Value("${spring.application.name:}") String applicationName) {
        HikariDataSource ds = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        ds.setUsername(resolveRuntimeJdbcRole(applicationName));
        return ds;
    }

    @Bean
    @Primary
    public DataSource dataSource(HikariDataSource hikariDataSource) {
        return new TenantAwareDataSource(hikariDataSource);
    }

    /**
     * Owner-role pool for bootstrap / bypass-RLS jobs. Prefers the Flyway JDBC URL so migrations
     * and owner work can stay on direct Postgres while app traffic uses PgBouncer.
     */
    @Bean("bootstrapDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource bootstrapDataSource(
            DataSourceProperties properties,
            @Value("${spring.flyway.url:}") String flywayUrl,
            @Value("${spring.flyway.user:app_owner}") String flywayUser,
            @Value("${spring.flyway.password:}") String flywayPassword) {
        String url = (flywayUrl != null && !flywayUrl.isBlank()) ? flywayUrl : properties.determineUrl();
        url = ensurePrepareThresholdDisabled(url);
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .username(flywayUser)
                .password(flywayPassword)
                .driverClassName(properties.getDriverClassName())
                .build();
    }

    /**
     * Pin the runtime pool to the plane's least-privilege role so {@code DB_USER} /
     * {@code SPRING_DATASOURCE_USERNAME} cannot point the WMS at {@code app_owner}.
     */
    static String resolveRuntimeJdbcRole(String applicationName) {
        return "invsys-admin-api".equals(applicationName) ? "app_owner" : "app_user";
    }

    /** PgBouncer transaction pooling: disable server-side prepared statements. */
    static String ensurePrepareThresholdDisabled(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank() || jdbcUrl.contains("prepareThreshold=")) {
            return jdbcUrl;
        }
        return jdbcUrl.contains("?")
                ? jdbcUrl + "&prepareThreshold=0"
                : jdbcUrl + "?prepareThreshold=0";
    }
}
