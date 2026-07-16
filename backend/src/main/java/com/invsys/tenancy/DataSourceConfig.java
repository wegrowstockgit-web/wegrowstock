package com.invsys.tenancy;

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
    public HikariDataSource hikariDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
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
            @Value("${spring.flyway.password:app_owner_secret}") String flywayPassword) {
        String url = (flywayUrl != null && !flywayUrl.isBlank()) ? flywayUrl : properties.determineUrl();
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .username(flywayUser)
                .password(flywayPassword)
                .driverClassName(properties.getDriverClassName())
                .build();
    }
}
