package com.invsys.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guardrail: core foundation packages must not depend on business feature modules.
 * Feature modules may depend on core and (transitionally) on sibling modules via services/DTOs.
 */
class ModularMonolithBoundaryTest {

    private static final Path CORE_ROOT = Path.of("..", "invsys-core", "src", "main", "java", "com", "invsys", "core")
            .normalize()
            .toAbsolutePath();

    @Test
    void coreFoundationDoesNotImportFeatureModules() throws IOException {
        assertTrue(Files.isDirectory(CORE_ROOT), "Missing core sources at " + CORE_ROOT);
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(CORE_ROOT)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    String text = Files.readString(path);
                    if (text.contains("com.invsys.modules.")) {
                        violations.add(CORE_ROOT.relativize(path) + " imports com.invsys.modules.*");
                    }
                } catch (IOException e) {
                    fail("Failed reading " + path + ": " + e.getMessage());
                }
            });
        }
        assertTrue(violations.isEmpty(), "Core must not import feature modules:\n" + String.join("\n", violations));
    }

    @Test
    void featureModulePackagesExist() {
        Path modules = Path.of("..", "invsys-core", "src", "main", "java", "com", "invsys", "modules").normalize();
        for (String name : List.of("catalog", "inventory", "purchasing", "sales", "fulfillment", "fintech")) {
            assertTrue(Files.isDirectory(modules.resolve(name)), "Missing module package: " + name);
        }
    }
}
