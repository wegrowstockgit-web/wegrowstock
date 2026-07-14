package com.invsys;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("invsys")
            .withUsername("app_owner")
            .withPassword("app_owner_secret")
            .withInitScript("db/test-init.sql");

    /** S3-compatible emulator for media tests (MinIO). */
    @SuppressWarnings("resource")
    static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"))
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).withStartupTimeout(Duration.ofSeconds(60)));

    static {
        POSTGRES.start();
        MINIO.start();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "app_user");
        registry.add("spring.datasource.password", () -> "app_user_secret");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "app_owner");
        registry.add("spring.flyway.password", () -> "app_owner_secret");

        registry.add("invsys.media.provider", () -> "MINIO");
        registry.add("invsys.media.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("invsys.media.access-key", () -> "minioadmin");
        registry.add("invsys.media.secret-key", () -> "minioadmin");
        registry.add("invsys.media.bucket", () -> "invsys-media-test");
        registry.add("invsys.media.region", () -> "us-east-1");
        registry.add("invsys.media.path-style-access", () -> "true");
        registry.add("invsys.media.create-bucket-if-missing", () -> "true");
    }
}
