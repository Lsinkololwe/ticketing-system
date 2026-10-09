package com.pml.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The collection registry is closed, and the code is checked against it.
 *
 * <h2>The registry is parsed, not copied</h2>
 * The registry table at {@link #SPEC} is the authority and this test reads it directly. A copy of the table in Java would be a second authority, and the two would
 * disagree within a release — at which point the lint enforces a registry nobody edits instead
 * of the one everybody reads.
 *
 * <h2>What the registry requires of every {@code @Document}</h2>
 * <ul>
 *   <li>its collection is a row of the registry — "no other collection exists"</li>
 *   <li>it carries {@code @TypeAlias}, so {@code _class} is a short stable alias rather than a
 *       package name that breaks the moment the class moves</li>
 *   <li>{@code @Version} exactly when the registry marks the row versioned</li>
 *   <li>timestamps are {@code Instant}, money is {@code BigDecimal}</li>
 * </ul>
 *
 * <h2>Ratchets where the debt is large</h2>
 * Most of these currently fail in bulk — the service prefix is missing from nearly every
 * collection, and 124 document fields are still {@code LocalDateTime}. Frozen budgets stop the
 * numbers rising while the migrations bring them down; a budget that is not lowered
 * after a fix fails the build, because reclaimed headroom silently permits new violations.
 */
@Tag("L1")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002-R2/R4/R5 · every @Document matches the closed collection registry")
class CollectionRegistryLintTest {

    private static final Path BACKEND_ROOT = Path.of("..");

    private static final Path SPEC =
            Path.of("../../specs/_platform/002-persistence-baseline/spec.md");

    /** A registry row: {@code | `booking_reservations` | **yes** | … }. */
    private static final Pattern REGISTRY_ROW =
            Pattern.compile("\\|\\s*`([a-z_]+)`\\s*\\|\\s*(yes|no|\\*\\*yes\\*\\*)\\s*\\|");

    /** Either {@code @Document(collection = "x")} or {@code @Document(collection = Xs.X)}. */
    private static final Pattern DOCUMENT =
            Pattern.compile("@Document\\s*\\(\\s*(?:collection\\s*=\\s*)?([^)]+?)\\s*\\)");

    /** {@code public static final String TICKETS = "booking_tickets";} in a *Collections class. */
    private static final Pattern COLLECTION_CONSTANT = Pattern.compile(
            "public static final String\\s+(\\w+)\\s*=\\s*\"([a-z_]+)\"");

    private static final Pattern FIELD = Pattern.compile(
            "^\\s*(?:private|public|protected)?\\s*([A-Za-z_][\\w.]*)\\s+([a-zA-Z_]\\w*)\\s*(?:=|;)",
            Pattern.MULTILINE);

    @Test
    @DisplayName("the registry agrees with its own stated total")
    void registryIsSelfConsistent() throws IOException {
        String section4 = section4();
        List<String> rows = registryRows(section4).keySet().stream().toList();

        Matcher stated = Pattern.compile("\\*\\*(\\d+) collections").matcher(section4);
        assertThat(stated.find()).as("§4 no longer states a total — the closing sentence moved").isTrue();

        assertThat(rows.size())
                .as("""
                    §4's table and §4's own sentence disagree. Every other spec's \
                    persistence.collections is reconciled against this table, and the plan cites \
                    the stated total as the number that reconciled — so a table the prose \
                    miscounts is a registry that cannot be trusted to be closed.""")
                .isEqualTo(Integer.parseInt(stated.group(1)));
    }

    @Test
    @DisplayName("every declared collection is a row of the registry")
    void everyDocumentNamesARegistryRow() throws IOException {
        Set<String> registry = registryRows(section4()).keySet();

        List<String> unregistered = new ArrayList<>();
        for (Document document : documents()) {
            if (!registry.contains(document.collection())) {
                unregistered.add(document.collection());
            }
        }

        // An unregistered collection is settled by its callers, not its name: one reachable
        // from nothing but its own repository is deleted; a live feature becomes a registry row.
        assertThat(new LinkedHashSet<>(unregistered))
                .as("""
                    §4 is a closed registry: "no other collection exists". A collection outside \
                    it has no owning service recorded anywhere, because the prefix is the only \
                    place write-ownership is written down.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no collection is written by two classes")
    void oneClassPerCollection() throws IOException {
        Map<String, List<String>> byCollection = new LinkedHashMap<>();
        for (Document document : documents()) {
            byCollection.computeIfAbsent(document.collection(), k -> new ArrayList<>())
                    .add(document.className());
        }

        List<String> shared = byCollection.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> e.getKey() + " ← " + String.join(", ", e.getValue()))
                .toList();

        // The failure appears most easily as a side effect of a rename: pointing a second
        // document at an existing constant is a one-line change no compiler objects to. A service
        // that reads a shared engine (reference data, platform settings) does so through a
        // shared-library reader over a raw document, never a second @Document class.
        assertThat(shared)
                .as("""
                    Two classes on one collection means two services can write it, and the \
                    second writer is invisible from the first one's code. Whichever service does \
                    not own the row reads it over the graph or the bus instead.""")
                .isEmpty();
    }

    @Test
    @DisplayName("documents on registry collections carry @TypeAlias")
    void registryDocumentsCarryATypeAlias() throws IOException {
        Set<String> registry = registryRows(section4()).keySet();

        List<String> missing = documents().stream()
                .filter(d -> registry.contains(d.collection()) && !d.hasTypeAlias())
                .map(d -> d.collection() + " → " + d.className())
                .toList();

        // No budget: every registry document carries one. Keep it that way.
        assertThat(missing)
                .as("""
                    Without @TypeAlias, Spring Data writes the fully-qualified class name into \
                    _class. Moving or renaming the class then makes every existing document \
                    unreadable, and nothing about the refactor looks dangerous at the time.""")
                .hasSizeLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("@Version is present exactly where the registry says versioned")
    void versionedRowsCarryVersion() throws IOException {
        Map<String, Boolean> registry = registryRows(section4());

        List<String> unprotected = new ArrayList<>();
        List<String> extra = new ArrayList<>();
        for (Document document : documents()) {
            Boolean versioned = registry.get(document.collection());
            if (versioned == null) {
                continue;                       // not a registry row — covered by another test
            }
            if (versioned && !document.hasVersion()) {
                unprotected.add(document.collection() + " → " + document.className());
            } else if (!versioned && document.hasVersion()) {
                extra.add(document.collection() + " → " + document.className());
            }
        }

        // The two directions are not symmetrical, so they are not asserted the same way.
        //
        // A versioned row with no @Version is a lost update waiting to happen, and there is no
        // reading of the registry under which it is intended. Hard failure, no budget.
        assertThat(unprotected)
                .as("""
                    @Version is what makes a concurrent update fail instead of silently \
                    overwriting. On a balance-bearing document the overwrite is money, and the \
                    registry is where the decision was recorded.""")
                .isEmpty();

        // The reverse — @Version on a row the registry marks unversioned — is a genuine
        // disagreement between the code and the registry, and it is NOT resolved by deleting the
        // annotation. Stripping optimistic locking can only make concurrency worse, so when the
        // two disagree the registry is corrected, as it was for ten booking rows on 2026-09-19.
        assertThat(extra)
                .as("a document is protected where §4 says it need not be — either §4's "
                        + "Versioned column is wrong, or the annotation is. Decide, do not drift.")
                .isEmpty();
    }

    @Test
    @DisplayName("money-named document fields are BigDecimal, never floating point")
    void moneyIsNeverAFloat() throws IOException {
        Set<String> floating = Set.of("double", "float", "Double", "Float");
        List<String> violations = new ArrayList<>();

        for (Document document : documents()) {
            Matcher field = FIELD.matcher(document.body());
            while (field.find()) {
                String type = field.group(1);
                String name = field.group(2);
                if (floating.contains(type) && looksLikeMoney(name)) {
                    violations.add("%s.%s : %s".formatted(document.className(), name, type));
                }
            }
        }

        // Deliberately keyed on the field name rather than banning `Double` outright:
        // Location.latitude and Location.longitude are coordinates, and a BigDecimal geo point
        // is wrong in a different way — the 2dsphere index would not accept it.
        assertThat(violations)
                .as("""
                    A binary float cannot represent K0.10 exactly, so the error compounds over a \
                    ledger and the books stop balancing by cents nobody can locate. §4 requires \
                    BigDecimal stored as Decimal128.""")
                .isEmpty();
    }

    @Test
    @DisplayName("document timestamps do not regress toward LocalDateTime")
    void documentTimestampsAreInstant() throws IOException {
        Set<String> banned = Set.of("LocalDateTime", "ZonedDateTime", "Date", "OffsetDateTime");

        Map<String, Integer> perModule = new LinkedHashMap<>();
        for (Document document : documents()) {
            Matcher field = FIELD.matcher(document.body());
            while (field.find()) {
                if (banned.contains(field.group(1))) {
                    perModule.merge(document.module(), 1, Integer::sum);
                }
            }
        }

        // Budgeted per module. LocalDateTime has no offset, so what a
        // document means depends on the JVM's zone at write time — two services in different
        // zones read the same document differently, and a reservation TTL computed from it
        // expires at the wrong moment. The fix is migrating the field type to Instant.
        Map<String, Integer> budget = Map.of(
                "booking-service", 0,
                "catalog-service", 0,
                "identity-service", 0);

        List<String> regressions = new ArrayList<>();
        List<String> unlowered = new ArrayList<>();
        budget.forEach((module, frozen) -> {
            int actual = perModule.getOrDefault(module, 0);
            if (actual > frozen) {
                regressions.add("%s: %d banned timestamp fields, budget %d".formatted(module, actual, frozen));
            } else if (actual < frozen) {
                unlowered.add("%s: down to %d from %d — lower the budget to lock the gain in"
                        .formatted(module, actual, frozen));
            }
        });

        assertThat(regressions).as("a new document field on a zoneless time type").isEmpty();
        assertThat(unlowered).as("a ratchet that is not tightened stops ratcheting").isEmpty();
    }

    // --------------------------------------------------------------------- helpers

    private record Document(String module, String collection, String className,
                            boolean hasTypeAlias, boolean hasVersion, String body) {
    }

    private static boolean looksLikeMoney(String fieldName) {
        String lower = fieldName.toLowerCase();
        return Stream.of("amount", "price", "fee", "balance", "total", "commission",
                        "revenue", "payout", "refund", "cost", "subtotal")
                .anyMatch(lower::contains);
    }

    private static String section4() throws IOException {
        String spec = Files.readString(SPEC);
        assertThat(spec).as("ET-PLT-002's spec is not where this test expects it").contains("## 4. Model");
        return spec.split("## 4\\. Model")[1].split("### Index registry")[0];
    }

    /** Collection name → versioned, in table order. */
    private static Map<String, Boolean> registryRows(String section4) {
        Map<String, Boolean> rows = new LinkedHashMap<>();
        Matcher matcher = REGISTRY_ROW.matcher(section4);
        while (matcher.find()) {
            rows.put(matcher.group(1), matcher.group(2).contains("yes"));
        }
        assertThat(rows).as("no registry rows parsed — §4's table format changed").isNotEmpty();
        return rows;
    }

    /**
     * {@code BookingCollections.TICKETS} → {@code booking_tickets}.
     *
     * <p>The declarations name a constant rather than a literal, which is the point of BE-3 —
     * so this lint has to resolve the constant or it would scan five documents and pass.</p>
     */
    private static Map<String, String> collectionConstants() throws IOException {
        Map<String, String> constants = new LinkedHashMap<>();
        try (Stream<Path> modules = Files.list(BACKEND_ROOT)) {
            for (Path module : modules.toList()) {
                Path sourceRoot = module.resolve("src/main/java");
                if (!Files.isDirectory(sourceRoot)) {
                    continue;
                }
                try (Stream<Path> sources = Files.walk(sourceRoot)) {
                    for (Path path : sources.filter(p -> p.getFileName().toString().endsWith("Collections.java")).toList()) {
                        String holder = path.getFileName().toString().replace(".java", "");
                        Matcher constant = COLLECTION_CONSTANT.matcher(Files.readString(path));
                        while (constant.find()) {
                            constants.put(holder + "." + constant.group(1), constant.group(2));
                        }
                    }
                }
            }
        }
        assertThat(constants)
                .as("no collection constants found — the *Collections holders moved or changed shape")
                .isNotEmpty();
        return constants;
    }

    private static String resolve(String reference, Map<String, String> constants) {
        String trimmed = reference.trim();
        if (trimmed.startsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return constants.get(trimmed);
    }

    private static List<Document> documents() throws IOException {
        Map<String, String> constants = collectionConstants();
        List<Document> documents = new ArrayList<>();
        try (Stream<Path> modules = Files.list(BACKEND_ROOT)) {
            for (Path module : modules.toList()) {
                Path sourceRoot = module.resolve("src/main/java");
                if (!Files.isDirectory(sourceRoot)) {
                    continue;
                }
                try (Stream<Path> sources = Files.walk(sourceRoot)) {
                    for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                        String body = Files.readString(path);
                        Matcher matcher = DOCUMENT.matcher(body);
                        if (!matcher.find()) {
                            continue;
                        }
                        String collection = resolve(matcher.group(1), constants);
                        assertThat(collection)
                                .as("%s declares @Document(%s), which resolves to no known "
                                        + "collection name", path, matcher.group(1))
                                .isNotNull();
                        String fileName = path.getFileName().toString();
                        documents.add(new Document(
                                module.getFileName().toString(),
                                collection,
                                fileName.substring(0, fileName.length() - ".java".length()),
                                body.contains("@TypeAlias"),
                                body.contains("@Version"),
                                body));
                    }
                }
            }
        }
        assertThat(documents)
                .as("no @Document classes found — an empty sweep is not a passing lint")
                .hasSizeGreaterThan(40);
        return documents;
    }

    @Test
    @DisplayName("no service asks the driver to ignore the replica set")
    void noServiceUsesDirectConnection() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> modules = Files.list(BACKEND_ROOT)) {
            for (Path module : modules.toList()) {
                Path resources = module.resolve("src/main/resources");
                if (!Files.isDirectory(resources)) {
                    continue;
                }
                try (Stream<Path> configs = Files.list(resources)) {
                    for (Path config : configs.filter(p -> p.getFileName().toString().endsWith(".yml")).toList()) {
                        for (String line : Files.readString(config).split("\n", -1)) {
                            if (line.contains("directConnection=true") && !line.strip().startsWith("#")) {
                                offenders.add(BACKEND_ROOT.relativize(config).toString());
                            }
                        }
                    }
                }
            }
        }

        assertThat(offenders)
                .as("""
                    directConnection=true tells the driver to treat the server as a single node,                     so it never discovers the replica set and multi-document transactions become                     SILENTLY INERT — D-01. Nothing errors: every write commits independently, and                     a reservation whose payment intent failed to persist becomes inventory nobody                     can buy and nobody can release.""")
                .isEmpty();
    }
}
