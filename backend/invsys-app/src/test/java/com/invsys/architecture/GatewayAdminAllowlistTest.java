package com.invsys.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: the control-plane Nginx server must deny the public internet and 403
 * when {@code $admin_allowed} is 0.
 */
class GatewayAdminAllowlistTest {

    private static final Path NGINX = Path.of("..", "..", "ops", "api-gateway", "nginx.conf").normalize();

    @Test
    void adminGeoDeniesByDefaultAndAllowsRfc1918() throws IOException {
        assertTrue(Files.isRegularFile(NGINX), "Missing gateway config at " + NGINX.toAbsolutePath());
        String conf = Files.readString(NGINX);
        assertTrue(conf.contains("geo $admin_allowed"), "geo $admin_allowed block missing");
        assertTrue(conf.contains("default 0;"), "admin geo must deny-all by default");
        assertTrue(conf.contains("10.0.0.0/8 1;"), "missing 10/8 allow");
        assertTrue(conf.contains("172.16.0.0/12 1;"), "missing 172.16/12 allow");
        assertTrue(conf.contains("192.168.0.0/16 1;"), "missing 192.168/16 allow");
        assertTrue(conf.contains("127.0.0.1 1;"), "missing loopback allow");
        assertTrue(conf.contains("if ($admin_allowed = 0)"), "missing $admin_allowed 403 gate");
        assertTrue(conf.contains("return 403;"), "admin deny path must return 403");
    }
}
