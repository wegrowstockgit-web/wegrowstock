#!/usr/bin/env python3
"""
One-shot Package-by-Feature migration for invsys-core (and import rewrites across backend).

Moves foundation packages under com.invsys.core.* and listed domain verticals under
com.invsys.modules.{catalog,inventory,purchasing,sales,fulfillment,fintech}.

Safe to re-run: skips files already at the destination.
"""
from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]  # backend/
JAVA_ROOTS = [
    ROOT / "invsys-core" / "src" / "main" / "java",
    ROOT / "invsys-app" / "src" / "main" / "java",
    ROOT / "invsys-app" / "src" / "test" / "java",
    ROOT / "invsys-chatbot" / "src" / "main" / "java",
    ROOT / "invsys-chatbot" / "src" / "test" / "java",
]

# Directory-level package renames (prefix substitution) — applied under each JAVA_ROOT that has them
DIR_PACKAGE_MOVES: list[tuple[str, str]] = [
    ("com/invsys/tenancy", "com/invsys/core/tenancy"),
    ("com/invsys/auth", "com/invsys/core/security"),
    ("com/invsys/common", "com/invsys/core/common"),
]

# Relative to invsys-core/src/main/java — class file → new package dir (under com/invsys/...)
CLASS_MOVES: dict[str, str] = {}


def add_module(module: str, layer: str, *simple_names: str, src_pkg: str) -> None:
    """Map SimpleName.java from src_pkg into modules.<module>.<layer>."""
    for name in simple_names:
        src = f"com/invsys/{src_pkg}/{name}.java"
        CLASS_MOVES[src] = f"com/invsys/modules/{module}/{layer}"


# --- Core foundation (selective from domain / integration) ---
CLASS_MOVES["com/invsys/domain/BaseEntity.java"] = "com/invsys/core/common"
CLASS_MOVES["com/invsys/domain/TenantScopedEntity.java"] = "com/invsys/core/common"
CLASS_MOVES["com/invsys/integration/OutboxService.java"] = "com/invsys/core/integration"
CLASS_MOVES["com/invsys/integration/CredentialVaultService.java"] = "com/invsys/core/integration"
CLASS_MOVES["com/invsys/domain/OutboxEvent.java"] = "com/invsys/core/integration"
CLASS_MOVES["com/invsys/repository/OutboxEventRepository.java"] = "com/invsys/core/integration"
CLASS_MOVES["com/invsys/repository/OutboxEventRepositoryCustom.java"] = "com/invsys/core/integration"
CLASS_MOVES["com/invsys/repository/OutboxEventRepositoryImpl.java"] = "com/invsys/core/integration"
CLASS_MOVES["com/invsys/integration/OutboxDispatcher.java"] = "com/invsys/core/integration"
CLASS_MOVES["com/invsys/integration/OutboxDispatchedEvent.java"] = "com/invsys/core/integration"
CLASS_MOVES["com/invsys/integration/OutboxEventHandler.java"] = "com/invsys/core/integration"
CLASS_MOVES["com/invsys/integration/OutboxPollingConfig.java"] = "com/invsys/core/integration"

# --- Catalog ---
add_module(
    "catalog",
    "domain",
    "Product",
    "ProductVariant",
    "Lot",
    "Location",
    "ShippingCarton",
    src_pkg="domain",
)
add_module(
    "catalog",
    "repository",
    "ProductRepository",
    "ProductVariantRepository",
    "LotRepository",
    "LocationRepository",
    "ShippingCartonRepository",
    "ProductMediaRepository",
    "ProductCategoryRepository",
    src_pkg="repository",
)
add_module("catalog", "service", "VariantCatalogService", src_pkg="service")
add_module(
    "catalog",
    "api",
    "ProductController",
    "ProductVariantController",
    "ProductMediaController",
    src_pkg="api",
)

# --- Inventory ---
add_module(
    "inventory",
    "domain",
    "InventoryLedger",
    "InventoryLevel",
    "LicensePlate",
    "CycleCount",
    "CycleCountLine",
    src_pkg="domain",
)
add_module(
    "inventory",
    "repository",
    "InventoryLedgerRepository",
    "InventoryLevelRepository",
    "InventoryLevelDeltaFlushRepository",
    "InventoryLevelDeltaFlushRepositoryImpl",
    "LicensePlateRepository",
    "CycleCountRepository",
    "CycleCountLineRepository",
    src_pkg="repository",
)
add_module(
    "inventory",
    "service",
    "InventoryService",
    "LpnService",
    "CycleCountService",
    src_pkg="service",
)

# --- Purchasing ---
add_module(
    "purchasing",
    "domain",
    "PurchaseOrder",
    "PurchaseOrderLine",
    "Supplier",
    "ApInvoiceIngestion",
    src_pkg="domain",
)
add_module(
    "purchasing",
    "repository",
    "PurchaseOrderRepository",
    "PurchaseOrderLineRepository",
    "SupplierRepository",
    "ApInvoiceIngestionRepository",
    "SupplierInvoiceIngestionRepository",
    "SupplierUserMappingRepository",
    src_pkg="repository",
)
add_module(
    "purchasing",
    "service",
    "PurchaseOrderService",
    "ApOcrIngestionService",
    src_pkg="service",
)
add_module(
    "purchasing",
    "api",
    "PurchaseOrderController",
    "ApInvoiceIngestionController",
    src_pkg="api",
)

# --- Sales ---
add_module(
    "sales",
    "domain",
    "SalesOrder",
    "SalesOrderLine",
    "Customer",
    "Invoice",
    "InvoiceLine",
    src_pkg="domain",
)
add_module(
    "sales",
    "repository",
    "SalesOrderRepository",
    "SalesOrderLineRepository",
    "CustomerRepository",
    "InvoiceRepository",
    "InvoiceLineRepository",
    "CustomerCatalogRestrictionRepository",
    "CustomerCreditLineRepository",
    "CustomerPriceTierRepository",
    "CustomerUserMappingRepository",
    src_pkg="repository",
)
add_module(
    "sales",
    "service",
    "SalesOrderService",
    "InvoicingService",
    src_pkg="service",
)
add_module(
    "sales",
    "api",
    "SalesOrderController",
    "InvoiceController",
    "CustomerBillingController",
    src_pkg="api",
)

# --- Fulfillment ---
add_module(
    "fulfillment",
    "domain",
    "Allocation",
    "PickingWave",
    "Shipment",
    "ShipmentLine",
    "FulfillmentException",
    src_pkg="domain",
)
add_module(
    "fulfillment",
    "repository",
    "AllocationRepository",
    "PickingWaveRepository",
    "ShipmentRepository",
    "ShipmentLineRepository",
    "FulfillmentExceptionRepository",
    src_pkg="repository",
)
add_module(
    "fulfillment",
    "service",
    "AllocationService",
    "PickingService",
    "ShipmentService",
    src_pkg="service",
)
add_module(
    "fulfillment",
    "api",
    "FulfillmentController",
    "PickingController",
    "PickingWaveController",
    src_pkg="api",
)

# --- Fintech ---
add_module(
    "fintech",
    "domain",
    "FactoredInvoice",
    "CapitalCreditLine",
    src_pkg="domain",
)
add_module(
    "fintech",
    "repository",
    "FactoredInvoiceRepository",
    "CapitalCreditLineRepository",
    src_pkg="repository",
)
add_module("fintech", "service", "FintechUnderwritingService", src_pkg="service")
add_module("fintech", "api", "FintechController", src_pkg="api")

# Import rewrite rules: longest-prefix first
IMPORT_REWRITES: list[tuple[str, str]] = []


def pkg_of(path_under_java: str) -> str:
    return path_under_java.replace("/", ".")


def register_rewrite(old_pkg: str, new_pkg: str) -> None:
    IMPORT_REWRITES.append((old_pkg, new_pkg))


# Directory moves
for old_dir, new_dir in DIR_PACKAGE_MOVES:
    register_rewrite(pkg_of(old_dir), pkg_of(new_dir))

# Class moves → package rewrite for each file's old package → new package
for src_rel, new_dir in CLASS_MOVES.items():
    old_pkg = pkg_of(str(Path(src_rel).parent).replace("\\", "/"))
    new_pkg = pkg_of(new_dir)
    if old_pkg != new_pkg:
        # Per-class FQCN rewrite is more precise than package-wide for domain/repository/service/api
        simple = Path(src_rel).stem
        IMPORT_REWRITES.append((f"{old_pkg}.{simple}", f"{new_pkg}.{simple}"))


def sort_rewrites(rules: list[tuple[str, str]]) -> list[tuple[str, str]]:
    # Longest old prefix first; dedupe keeping first
    seen: set[str] = set()
    out: list[tuple[str, str]] = []
    for old, new in sorted(rules, key=lambda x: len(x[0]), reverse=True):
        if old in seen or old == new:
            continue
        seen.add(old)
        out.append((old, new))
    return out


def move_tree(src: Path, dst: Path) -> list[tuple[Path, Path]]:
    moved: list[tuple[Path, Path]] = []
    if not src.exists():
        return moved
    for java in src.rglob("*.java"):
        rel = java.relative_to(src)
        target = dst / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            continue
        shutil.move(str(java), str(target))
        moved.append((java, target))
    # cleanup empty dirs
    for d in sorted(src.rglob("*"), reverse=True):
        if d.is_dir():
            try:
                d.rmdir()
            except OSError:
                pass
    try:
        src.rmdir()
    except OSError:
        pass
    return moved


def rewrite_package_decl(content: str, new_pkg: str) -> str:
    return re.sub(r"(?m)^package\s+[\w.]+;", f"package {new_pkg};", content, count=1)


def apply_import_rewrites(content: str, rules: list[tuple[str, str]]) -> str:
    # Word-boundary-ish: avoid partial package token collisions by longest-first replace
    for old, new in rules:
        content = content.replace(old, new)
    return content


def main() -> None:
    core_java = ROOT / "invsys-core" / "src" / "main" / "java"
    rules = sort_rewrites(IMPORT_REWRITES)

    print(f"Rewrite rules: {len(rules)}")
    moved_count = 0

    # 1) Directory package moves (entire trees) across all java roots (main + tests)
    for java_root in JAVA_ROOTS:
        for old_dir, new_dir in DIR_PACKAGE_MOVES:
            src = java_root / old_dir
            dst = java_root / new_dir
            if src.exists():
                print(f"DIR [{java_root.name}] {old_dir} -> {new_dir}")
                move_tree(src, dst)
                moved_count += 1

    # 2) Class file moves
    for src_rel, new_dir in CLASS_MOVES.items():
        src = core_java / src_rel
        if not src.exists():
            # already moved or missing
            continue
        dst_dir = core_java / new_dir
        dst_dir.mkdir(parents=True, exist_ok=True)
        dst = dst_dir / src.name
        if dst.exists():
            continue
        print(f"FILE {src_rel} -> {new_dir}/{src.name}")
        shutil.move(str(src), str(dst))
        moved_count += 1

    # 3) Fix package declarations for every java under new locations + apply import rewrites globally
    java_files: list[Path] = []
    for root in JAVA_ROOTS:
        if root.exists():
            java_files.extend(root.rglob("*.java"))

    changed = 0
    for path in java_files:
        text = path.read_text(encoding="utf-8")
        original = text

        # Infer package from path relative to .../java/
        rel = None
        for root in JAVA_ROOTS:
            try:
                rel = path.relative_to(root)
                break
            except ValueError:
                continue
        if rel is not None:
            pkg = pkg_of(str(rel.parent).replace("\\", "/"))
            if pkg.startswith("com.invsys"):
                text = rewrite_package_decl(text, pkg)

        text = apply_import_rewrites(text, rules)

        if text != original:
            path.write_text(text, encoding="utf-8", newline="\n")
            changed += 1

    print(f"Moved trees/files ops: {moved_count}")
    print(f"Files content-updated: {changed}")


if __name__ == "__main__":
    main()
