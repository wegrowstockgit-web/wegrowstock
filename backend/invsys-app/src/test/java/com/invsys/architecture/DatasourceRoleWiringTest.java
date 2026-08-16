package com.invsys.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data plane JDBC must be {@code app_user}; control plane JDBC must be {@code app_owner}.
 * Passwords are env-only — YAML must not embed {@code app_user_secret} / {@code app_owner_secret}.
 */
class DatasourceRoleWiringTest {

    @Test
    void dataPlaneYamlPinsAppUserAndExternalizesPassword() throws IOException {
        Path yml = Path.of("..", "invsys-core", "src", "main", "resources", "application.yml");
        String text = Files.readString(yml);
        assertTrue(text.contains("username: app_user"), "data plane username must be literal app_user");
        assertFalse(text.contains("${DB_USER"), "DB_USER must not override data-plane JDBC username");
        assertTrue(text.contains("${DB_APP_USER_PASSWORD"), "data plane password must use DB_APP_USER_PASSWORD");
        assertFalse(text.contains("app_user_secret"), "no hardcoded app_user_secret in data-plane YAML");
        assertFalse(text.contains("app_owner_secret"), "no hardcoded app_owner_secret in data-plane YAML");
    }

    @Test
    void controlPlaneYamlPinsAppOwnerAndExternalizesPassword() throws IOException {
        Path yml = Path.of("..", "invsys-admin-api", "src", "main", "resources", "application.yml");
        String text = Files.readString(yml);
        assertTrue(text.contains("username: app_owner"), "control plane username must be literal app_owner");
        assertFalse(text.contains("${DB_USER"), "DB_USER must not override control-plane JDBC username");
        assertTrue(text.contains("${DB_APP_OWNER_PASSWORD"), "control plane password must use DB_APP_OWNER_PASSWORD");
        assertFalse(text.contains("app_user_secret"), "no hardcoded app_user_secret in control-plane YAML");
        assertFalse(text.contains("app_owner_secret"), "no hardcoded app_owner_secret in control-plane YAML");
    }
}
