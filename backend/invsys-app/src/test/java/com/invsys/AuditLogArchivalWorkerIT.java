package com.invsys;

import com.invsys.metrics.WmsMetrics;
import com.invsys.service.AuditLogArchivalWorker;
import com.invsys.core.tenancy.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import com.invsys.media.S3ClientConfig;
import com.invsys.media.S3ObjectStorage;

/**
 * Integration suite for {@link AuditLogArchivalWorker}: LocalStack S3, Awaitility-driven
 * scheduled archival, and Toxiproxy chaos proving purge never runs on upload failure.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "spring.task.scheduling.enabled=true",
        "invsys.audit.archive.enabled=true",
        "invsys.audit.archive.retention-days=90",
        "invsys.audit.archive.batch-size=50",
        // Fire every 2s so Awaitility can observe the real @Scheduled cron path
        "invsys.audit.archive.cron=0/2 * * * * ?"
})
class AuditLogArchivalWorkerIT extends AbstractIntegrationTest {

    static {
        // Parent @DynamicPropertySource runs after ours and would otherwise force MinIO.
        useDefaultMinioMedia = false;
    }

    static final String ARCHIVE_BUCKET = "wms-audit-cold-storage-test";
    static final String ACTION_TAG = "ARCHIVE_IT_AGED";

    static final Network NETWORK = Network.newNetwork();

    /**
     * Pin pre-auth LocalStack (post-2026-03 {@code latest} requires LOCALSTACK_AUTH_TOKEN).
     */
    @Container
    @SuppressWarnings("resource")
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8.1"))
            .withNetwork(NETWORK)
            .withNetworkAliases("localstack")
            .withServices("s3");

    @Container
    @SuppressWarnings("resource")
    static final ToxiproxyContainer TOXIPROXY = new ToxiproxyContainer(
            DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.9.0"))
            .withNetwork(NETWORK);

    static volatile ToxiproxyContainer.ContainerProxy s3Proxy;
    static S3Client directS3; // bypasses Toxiproxy (bucket bootstrap + GetObject assertions)

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TestDataHelper testDataHelper;
    @Autowired AuditLogArchivalWorker archivalWorker;
    @Autowired MeterRegistry meterRegistry;

    private UUID tenantId;

    /**
     * DynamicPropertySource runs before {@code @BeforeAll}, so proxy + bucket bootstrap
     * must happen here once containers are up.
     */
    @DynamicPropertySource
    static void localStackAwsProperties(DynamicPropertyRegistry registry) {
        ensureLocalStackReady();

        // Blueprint: Spring Cloud AWS-style overrides (mapped for suite compliance)
        registry.add("spring.cloud.aws.s3.endpoint", AuditLogArchivalWorkerIT::toxiproxyS3Endpoint);
        registry.add("spring.cloud.aws.credentials.access-key", LOCALSTACK::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", LOCALSTACK::getSecretKey);
        registry.add("spring.cloud.aws.region.static", LOCALSTACK::getRegion);

        // App actually binds S3 via invsys.media.* (S3ClientConfig)
        registry.add("invsys.media.provider", () -> "AWS");
        registry.add("invsys.media.endpoint", AuditLogArchivalWorkerIT::toxiproxyS3Endpoint);
        registry.add("invsys.media.access-key", LOCALSTACK::getAccessKey);
        registry.add("invsys.media.secret-key", LOCALSTACK::getSecretKey);
        registry.add("invsys.media.region", LOCALSTACK::getRegion);
        registry.add("invsys.media.bucket", () -> ARCHIVE_BUCKET);
        registry.add("invsys.media.path-style-access", () -> "true");
        // Allow S3ObjectStorage bootstrap through the Toxiproxy hop
        registry.add("invsys.media.create-bucket-if-missing", () -> "true");
    }

    @BeforeAll
    static void verifyBucketExists() {
        ensureLocalStackReady();
        assertThat(directS3.listBuckets().buckets().stream().anyMatch(b -> ARCHIVE_BUCKET.equals(b.name())))
                .as("pre-flight bucket %s must exist in LocalStack", ARCHIVE_BUCKET)
                .isTrue();
    }

    @AfterAll
    static void restoreDefaultMinioMedia() {
        // Avoid poisoning later IT classes that share this JVM and expect MinIO defaults.
        useDefaultMinioMedia = true;
    }

    private static void ensureLocalStackReady() {
        if (s3Proxy == null) {
            s3Proxy = TOXIPROXY.getProxy(LOCALSTACK, 4566);
        }
        if (directS3 == null) {
            directS3 = S3Client.builder()
                    .endpointOverride(LOCALSTACK.getEndpoint())
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                    .region(Region.of(LOCALSTACK.getRegion()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();
            try {
                directS3.createBucket(CreateBucketRequest.builder().bucket(ARCHIVE_BUCKET).build());
            } catch (Exception alreadyExists) {
                // idempotent across context refreshes
            }
            directS3.putObject(
                    b -> b.bucket(ARCHIVE_BUCKET).key("_bootstrap.txt"),
                    RequestBody.fromString("ok"));
        }
    }

    private static String toxiproxyS3Endpoint() {
        // Host-side JVM must use the published Toxiproxy port (not the Docker-network IP).
        return "http://" + TOXIPROXY.getHost() + ":" + s3Proxy.getProxyPort();
    }

    @BeforeEach
    void createTenant() {
        clearS3ProxyToxics();
        tenantId = testDataHelper.createTenant(
                "Archive IT", "arch-it-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void cleanup() {
        clearS3ProxyToxics();
        TenantContext.clear();
    }

    @Test
    @Order(1)
    void shouldSuccessfullyArchiveLogsToS3() {
        insertAgedAuditRows(10, 95);

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(countAgedRows()).isZero();

                    List<S3Object> objects = listArchiveObjects(tenantId);
                    assertThat(objects)
                            .as("expected .jsonl.gz under archives/%s/", tenantId)
                            .isNotEmpty();

                    String key = objects.getFirst().key();
                    ResponseBytes<GetObjectResponse> bytes = directS3.getObjectAsBytes(
                            GetObjectRequest.builder().bucket(ARCHIVE_BUCKET).key(key).build());
                    assertThat(bytes.asByteArray().length).isGreaterThan(20);
                    try (GZIPInputStream gzip = new GZIPInputStream(bytes.asInputStream())) {
                        String jsonl = new String(gzip.readAllBytes());
                        assertThat(jsonl).contains(ACTION_TAG);
                        assertThat(jsonl.lines().count()).isEqualTo(10);
                    }
                });
    }

    @Test
    @Order(2)
    void shouldTriggerAlertOnS3Timeout() throws Exception {
        insertAgedAuditRows(5, 95);
        double failuresBefore = meterRegistry.counter(
                WmsMetrics.AUDIT_ARCHIVE_FAILURES, "tenant", tenantId.toString()).count();

        // Hard-cut the proxied path the Spring S3Client uses (Toxiproxy chaos)
        s3Proxy.setConnectionCut(true);

        try {
            // Same entrypoint as the cron — must not crash the Spring context
            archivalWorker.runNightlyArchival();

            assertThat(countAgedRows())
                    .as("rows must remain when S3 upload fails (no silent purge)")
                    .isEqualTo(5);

            assertThat(meterRegistry.counter(
                    WmsMetrics.AUDIT_ARCHIVE_FAILURES, "tenant", tenantId.toString()).count())
                    .as("ops alert counter must increment on archival failure")
                    .isGreaterThan(failuresBefore);

            assertThat(archivalWorker).isNotNull();
        } finally {
            s3Proxy.setConnectionCut(false);
            clearS3ProxyToxics();
        }
    }

    private void insertAgedAuditRows(int count, int daysAgo) {
        Instant createdAt = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        jdbcTemplate.execute((java.sql.Connection connection) -> {
            bindTenantOn(connection, tenantId);
            try (var insert = connection.prepareStatement("""
                    INSERT INTO audit_log (
                        id, tenant_id, actor_user_id, action, entity_type, entity_id,
                        diff, created_at, updated_at
                    ) VALUES (?, ?, NULL, ?, 'USER', ?, '{}'::jsonb, ?, ?)
                    """)) {
                for (int i = 0; i < count; i++) {
                    insert.setObject(1, UUID.randomUUID());
                    insert.setObject(2, tenantId);
                    insert.setString(3, ACTION_TAG);
                    insert.setObject(4, UUID.randomUUID());
                    insert.setTimestamp(5, java.sql.Timestamp.from(createdAt));
                    insert.setTimestamp(6, java.sql.Timestamp.from(createdAt));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            return null;
        });
        assertThat(countAgedRows()).isEqualTo(count);
    }

    private int countAgedRows() {
        Integer count = jdbcTemplate.execute((java.sql.Connection connection) -> {
            bindTenantOn(connection, tenantId);
            try (var ps = connection.prepareStatement("""
                    SELECT COUNT(*) FROM audit_log
                     WHERE tenant_id = ?
                       AND action = ?
                    """)) {
                ps.setObject(1, tenantId);
                ps.setString(2, ACTION_TAG);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
        return count == null ? 0 : count;
    }

    private static void bindTenantOn(java.sql.Connection connection, UUID id) throws java.sql.SQLException {
        try (var setTenant = connection.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, false)")) {
            setTenant.setString(1, id.toString());
            setTenant.execute();
        }
    }

    private List<S3Object> listArchiveObjects(UUID tenant) {
        // Prefer month prefix; fall back to tenant root if clock skew across TZ edges
        String monthPrefix = archivePrefix(tenant);
        ListObjectsV2Response listing = directS3.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(ARCHIVE_BUCKET)
                .prefix(monthPrefix)
                .build());
        List<S3Object> objects = listing.contents().stream()
                .filter(o -> o.key() != null && o.key().endsWith(".jsonl.gz"))
                .toList();
        if (!objects.isEmpty()) {
            return objects;
        }
        ListObjectsV2Response broad = directS3.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(ARCHIVE_BUCKET)
                .prefix("archives/" + tenant + "/")
                .build());
        return broad.contents().stream()
                .filter(o -> o.key() != null && o.key().endsWith(".jsonl.gz"))
                .toList();
    }

    private static String archivePrefix(UUID tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return "archives/" + tenantId + "/audit/"
                + String.format("%04d", today.getYear()) + "/"
                + String.format("%02d", today.getMonthValue()) + "/";
    }

    private static void clearS3ProxyToxics() {
        if (s3Proxy == null) {
            return;
        }
        try {
            s3Proxy.toxics().getAll().forEach(toxic -> {
                try {
                    toxic.remove();
                } catch (Exception ignored) {
                    // best-effort cleanup between tests
                }
            });
        } catch (Exception ignored) {
            // proxy may not be ready yet
        }
    }
}
