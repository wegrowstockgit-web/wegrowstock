package com.invsys.architecture;

import com.invsys.InvSysApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

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
 * Feature modules may depend on core and (transitionally) on sibling modules via named
 * interfaces ({@code api} / {@code domain}).
 *
 * <p>Event-migration plan for remaining shared-kernel coupling (not a Modulith
 * {@code verify()} failure today because the adapters live in {@code com.invsys.service},
 * outside {@code @ApplicationModule} packages):
 * <ol>
 *   <li>{@code ShipmentInvoiceSourceAdapter} reads fulfillment repositories to feed sales
 *       invoicing — publish {@code ShipmentCompleted} from fulfillment and handle it with
 *       {@code @Async @EventListener} in fintech/sales instead of repository hopping.</li>
 *   <li>{@code FulfillmentService} / {@code ReturnToVendorService} / {@code SpatialMapService}
 *       call fulfillment internals — move those facades into {@code modules.fulfillment} or
 *       replace cross-slice calls with application events ({@code InventoryAllocated},
 *       {@code RtvOpened}).</li>
 *   <li>Keep {@code allowedDependencies} tight: fintech may use {@code sales :: api/domain}
 *       only; never fulfillment {@code service} / {@code repository} packages.</li>
 * </ol>
 */
class ModularMonolithBoundaryTest {

    private static final Path CORE_ROOT = Path.of("..", "invsys-core", "src", "main", "java", "com", "invsys", "core")
            .normalize()
            .toAbsolutePath();

    @Test
    void verifyModulithArchitecture() {
        String previous = System.getProperty("spring.modulith.detection-strategy");
        System.setProperty("spring.modulith.detection-strategy",
                "com.invsys.modules.BoundedContextDetectionStrategy");
        try {
            ApplicationModules modules = ApplicationModules.of(InvSysApplication.class);
            modules.verify();
        } finally {
            if (previous == null) {
                System.clearProperty("spring.modulith.detection-strategy");
            } else {
                System.setProperty("spring.modulith.detection-strategy", previous);
            }
        }
    }

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
