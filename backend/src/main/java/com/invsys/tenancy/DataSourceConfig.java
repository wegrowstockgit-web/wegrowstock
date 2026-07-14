package com.invsys.tenancy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        DataSource raw = properties.initializeDataSourceBuilder().build();
        return new TenantAwareDataSource(raw);
    }

    @Bean("bootstrapDataSource")
    public DataSource bootstrapDataSource(
            DataSourceProperties properties,
            @Value("${spring.flyway.user:app_owner}") String flywayUser,
            @Value("${spring.flyway.password:app_owner_secret}") String flywayPassword) {
        return DataSourceBuilder.create()
                .url(properties.determineUrl())
                .username(flywayUser)
                .password(flywayPassword)
                .driverClassName(properties.getDriverClassName())
                .build();
    }
}
