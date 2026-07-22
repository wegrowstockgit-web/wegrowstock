#!/usr/bin/env python3
"""
Resolve missing type imports after package moves.

Builds a SimpleName -> FQCN index from all main/test sources, then for each file
adds imports for referenced types that are not in the same package and not already imported.
Ambiguous simple names (multiple FQCNs) are skipped.
"""
from __future__ import annotations

import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOTS = [
    ROOT / "invsys-core" / "src" / "main" / "java",
    ROOT / "invsys-app" / "src" / "main" / "java",
    ROOT / "invsys-app" / "src" / "test" / "java",
    ROOT / "invsys-chatbot" / "src" / "main" / "java",
    ROOT / "invsys-chatbot" / "src" / "test" / "java",
]

TYPE_DECL = re.compile(
    r"(?m)^(?:public\s+|protected\s+|private\s+|abstract\s+|final\s+|sealed\s+|non-sealed\s+)*"
    r"(?:class|interface|enum|record)\s+(\w+)"
)
PKG_RE = re.compile(r"(?m)^package\s+([\w.]+);")
# Type-ish tokens: ClassName shapes (avoid all-caps constants)
TYPE_TOKEN = re.compile(r"\b([A-Z][A-Za-z0-9]+)\b")

# JDK / common types — never auto-import from our index
SKIP_SIMPLE = {
    "String", "Integer", "Long", "Double", "Float", "Boolean", "Byte", "Short", "Character",
    "Object", "Class", "Void", "Override", "Deprecated", "SuppressWarnings", "SafeVarargs",
    "List", "Map", "Set", "Optional", "UUID", "BigDecimal", "BigInteger", "LocalDate",
    "LocalDateTime", "Instant", "Duration", "OffsetDateTime", "ZonedDateTime", "Date",
    "ArrayList", "HashMap", "HashSet", "LinkedHashMap", "LinkedHashSet", "Collections",
    "Objects", "Arrays", "Collectors", "Stream", "Path", "Files", "Paths", "URI", "URL",
    "Exception", "RuntimeException", "Throwable", "Error", "IllegalArgumentException",
    "IllegalStateException", "UnsupportedOperationException", "NullPointerException",
    "IOException", "InterruptedException", "Serializable", "Cloneable", "AutoCloseable",
    "System", "Math", "Thread", "Runnable", "Comparable", "Comparator", "Iterable",
    "Iterator", "Enumeration", "Properties", "Locale", "Charset", "StandardCharsets",
    "Pattern", "Matcher", "Logger", "Slf4j", "Service", "Component", "Repository",
    "Controller", "RestController", "Configuration", "Bean", "Autowired", "Value",
    "Transactional", "Entity", "Table", "Id", "Column", "GeneratedValue", "ManyToOne",
    "OneToMany", "OneToOne", "ManyToMany", "JoinColumn", "Enumerated", "EnumType",
    "Embedded", "Embeddable", "Transient", "Version", "Lob", "Convert", "Converter",
    "JsonIgnore", "JsonProperty", "JsonAlias", "JsonInclude", "JsonFormat",
    "BeforeEach", "AfterEach", "Test", "DisplayName", "Nested", "Mock", "InjectMocks",
    "MockBean", "Autowired", "SpringBootTest", "DataJpaTest", "WebMvcTest",
    "Assert", "Assertions", "Mockito", "ArgumentCaptor", "ParameterizedTest",
    "MethodSource", "CsvSource", "ValueSource", "Disabled", "Tag", "Order",
    "BeforeAll", "AfterAll", "TestInstance", "ExtendWith", "RegisterExtension",
    "HttpStatus", "ResponseEntity", "HttpHeaders", "MediaType", "MockMvc",
    "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "PatchMapping",
    "DeleteMapping", "PathVariable", "RequestBody", "RequestParam", "RequestHeader",
    "Valid", "NotNull", "NotBlank", "Size", "Min", "Max", "Positive", "DecimalMin",
    "Builder", "Data", "Getter", "Setter", "RequiredArgsConstructor", "AllArgsConstructor",
    "NoArgsConstructor", "EqualsAndHashCode", "ToString", "SuperBuilder",
    "Page", "Pageable", "PageRequest", "Sort", "Direction", "JpaRepository",
    "CrudRepository", "Query", "Param", "Modifying", "Lock", "LockModeType",
    "EntityManager", "PersistenceContext", "TypedQuery", "CriteriaBuilder",
    "ApplicationEvent", "ApplicationEventPublisher", "EventListener", "TransactionalEventListener",
    "Scheduled", "Async", "EnableScheduling", "ConfigurationProperties",
    "RestClient", "WebClient", "ObjectMapper", "JsonNode", "JavaType",
    "Filter", "OncePerRequestFilter", "FilterChain", "HttpServletRequest",
    "HttpServletResponse", "ServletException", "Authentication", "SecurityContext",
    "SecurityContextHolder", "UserDetails", "GrantedAuthority", "UsernamePasswordAuthenticationToken",
    "PasswordEncoder", "BCryptPasswordEncoder", "CorsConfiguration", "CorsConfigurationSource",
    "SecurityFilterChain", "HttpSecurity", "AuthenticationManager", "AuthenticationConfiguration",
    "Jwt", "Claims", "Jwts", "Keys", "SecretKey", "Key", "PublicKey", "PrivateKey",
    "Cipher", "SecureRandom", "Base64", "MessageDigest", "Mac", "SecretKeySpec",
    "GCMParameterSpec", "ByteBuffer", "ByteArrayOutputStream", "ByteArrayInputStream",
    "InputStream", "OutputStream", "Reader", "Writer", "BufferedReader", "PrintWriter",
    "Connection", "PreparedStatement", "ResultSet", "SQLException", "DataSource",
    "JdbcTemplate", "NamedParameterJdbcTemplate", "TransactionTemplate",
    "Clock", "ZoneId", "ZoneOffset", "Temporal", "ChronoUnit",
    "Consumer", "Function", "Supplier", "Predicate", "BiFunction", "BiConsumer",
    "UnaryOperator", "BinaryOperator", "CompletableFuture", "Executor", "ExecutorService",
    "AtomicBoolean", "AtomicInteger", "AtomicLong", "AtomicReference",
    "ConcurrentHashMap", "CopyOnWriteArrayList", "ReentrantLock", "ReadWriteLock",
    "Duration", "Period", "YearMonth", "MonthDay", "DayOfWeek", "Month",
    "SoftDelete", "Where", "SQLRestriction", "Formula", "CreationTimestamp", "UpdateTimestamp",
    "Record", "Sealed", "NonNull", "Nullable", "VisibleForTesting",
}


def index_types() -> dict[str, set[str]]:
    index: dict[str, set[str]] = defaultdict(set)
    for root in JAVA_ROOTS:
        if not root.exists():
            continue
        for path in root.rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            m = PKG_RE.search(text)
            if not m:
                continue
            pkg = m.group(1)
            for dm in TYPE_DECL.finditer(text):
                simple = dm.group(1)
                index[simple].add(f"{pkg}.{simple}")
    return index


def existing_imports(text: str) -> set[str]:
    return set(re.findall(r"(?m)^import\s+(?:static\s+)?([\w.]+);", text))


def insert_imports(text: str, fqcns: list[str]) -> str:
    if not fqcns:
        return text
    lines = text.splitlines(keepends=True)
    insert_at = 0
    for i, line in enumerate(lines):
        if line.startswith("package ") or line.startswith("import "):
            insert_at = i + 1
    block = "".join(f"import {fq};\n" for fq in sorted(set(fqcns)))
    lines.insert(insert_at, block)
    return "".join(lines)


def main() -> None:
    index = index_types()
    unique = {k: next(iter(v)) for k, v in index.items() if len(v) == 1}
    print(f"Unique type index size: {len(unique)} (ambiguous skipped: {sum(1 for v in index.values() if len(v) > 1)})")

    changed = 0
    for root in JAVA_ROOTS:
        if not root.exists():
            continue
        for path in root.rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            m = PKG_RE.search(text)
            if not m:
                continue
            file_pkg = m.group(1)
            imports = existing_imports(text)
            imported_simple = {i.rsplit(".", 1)[-1] for i in imports if not i.endswith(".*")}
            star_pkgs = {i[:-2] for i in imports if i.endswith(".*")}

            # types declared in this file
            declared = {dm.group(1) for dm in TYPE_DECL.finditer(text)}

            missing: list[str] = []
            for simple in set(TYPE_TOKEN.findall(text)):
                if simple in SKIP_SIMPLE or simple in declared or simple in imported_simple:
                    continue
                fqcn = unique.get(simple)
                if not fqcn:
                    continue
                pkg = fqcn.rsplit(".", 1)[0]
                if pkg == file_pkg or pkg in star_pkgs:
                    continue
                # only add if looks like a type use (extends/implements/new/field/param)
                if not re.search(
                    rf"(extends|implements|new|@|\(|,|\s|<)\s*{re.escape(simple)}\b"
                    rf"|\b{re.escape(simple)}\s*<"
                    rf"|\b{re.escape(simple)}\s+\w+"
                    rf"|\b{re.escape(simple)}\.",
                    text,
                ):
                    continue
                missing.append(fqcn)

            if not missing:
                continue
            new_text = insert_imports(text, missing)
            if new_text != text:
                path.write_text(new_text, encoding="utf-8", newline="\n")
                changed += 1

    print(f"Updated files: {changed}")


if __name__ == "__main__":
    main()
