package com.pml.shared.testing;

import com.pml.shared.persistence.IndexSpec;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts a live database carries the indexes the index registry declares.
 *
 * <h2>Why a live server and not the declared list</h2>
 * Checking that the code declares the right {@link IndexSpec}s only proves the list matches
 * itself. What matters is what the server ended up with, and the two come apart routinely: an
 * index that already exists under another name is refused with error 85, a unique index built
 * over data that already contains duplicates fails, and a TTL index whose field is a string
 * rather than a date is created and never expires anything.
 *
 * <h2>Uniqueness is checked as a property, not as a flag</h2>
 * {@code listIndexes} reporting {@code unique: true} is one thing; the server actually refusing
 * a duplicate is the thing that matters. {@link #assertRefusesDuplicate} inserts the same value
 * twice, because a unique index built with {@code sparse} over the wrong field will report
 * exactly the same metadata and refuse nothing.
 */
public final class IndexRegistryAssertions {

    private final ReactiveMongoTemplate template;

    public IndexRegistryAssertions(ReactiveMongoTemplate template) {
        this.template = template;
    }

    /**
     * The declarations match the index registry table, row for row.
     *
     * <p>{@link #assertAllPresent} compares the server to the code, which cannot detect the
     * code disagreeing with the registry — change a declaration and the server follows it,
     * so both move together and the check stays green. The registry table is the authority, so
     * it is parsed and compared directly.</p>
     *
     * @param prefix the service's collection prefix, e.g. {@code booking_}
     */
    public static void assertMatchesRegistry(List<IndexSpec> specs, String prefix) {
        java.nio.file.Path spec = java.nio.file.Path.of(
                "../../specs/_platform/002-persistence-baseline/spec.md");
        String section;
        try {
            section = java.nio.file.Files.readString(spec)
                    .split("### Index registry")[1].split("### Redis key registry")[0];
        } catch (java.io.IOException e) {
            throw new IllegalStateException("ET-PLT-002's spec is not where this test expects it", e);
        }

        java.util.regex.Pattern rowPattern = java.util.regex.Pattern.compile(
                "\\|\\s*`([a-z_]+)`\\s*\\|\\s*`([^`]+)`\\s*\\|\\s*([^|]+)\\|");
        java.util.regex.Matcher matcher = rowPattern.matcher(section);

        // Rows, not a map: a collection may carry two indexes on the same fields that differ in
        // options — a partial unique index beside a plain one — and each is its own row.
        record Row(String collection, List<String> fields, String kind) {
        }
        List<Row> expected = new ArrayList<>();
        while (matcher.find()) {
            String collection = matcher.group(1);
            if (!collection.startsWith(prefix)) {
                continue;
            }
            List<String> fields = new ArrayList<>();
            for (String part : matcher.group(2).replaceAll("[{}]", "").split(",")) {
                if (part.contains(":")) {
                    fields.add(part.split(":")[0].trim().replace("\"", "").replace("'", ""));
                }
            }
            expected.add(new Row(collection, fields, matcher.group(3).trim()));
        }

        assertThat(expected).as("no §4 rows parsed for prefix %s", prefix).isNotEmpty();

        List<String> problems = new ArrayList<>();
        java.util.Set<IndexSpec> used = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Row row : expected) {
            boolean wantsUnique = row.kind().contains("unique");
            // Prefer the declaration whose uniqueness matches the row, so two rows on the same
            // fields pair with the right two declarations whichever order they are listed in.
            IndexSpec declared = specs.stream()
                    .filter(s -> !used.contains(s))
                    .filter(s -> s.collection().equals(row.collection()))
                    .filter(s -> new ArrayList<>(s.keys().keySet()).equals(row.fields()))
                    .sorted(java.util.Comparator.comparing(s -> s.unique() == wantsUnique ? 0 : 1))
                    .findFirst()
                    .orElse(null);

            if (declared == null) {
                problems.add("§4 declares %s %s and no IndexSpec matches it in that key order"
                        .formatted(row.collection(), row.fields()));
                continue;
            }
            used.add(declared);
            if (wantsUnique && !declared.unique()) {
                problems.add("%s %s is unique in §4 but not in the declaration"
                        .formatted(row.collection(), row.fields()));
            }
            if (row.kind().contains("sparse") && !declared.sparse()) {
                problems.add("%s %s is sparse in §4 but not in the declaration"
                        .formatted(row.collection(), row.fields()));
            }
            if (row.kind().contains("TTL") && declared.expireAfter() == null) {
                problems.add("%s %s is a TTL index in §4 but declares no expiry"
                        .formatted(row.collection(), row.fields()));
            }
        }

        assertThat(problems)
                .as("the index declarations have drifted from ET-PLT-002 §4, which is the authority")
                .isEmpty();

        assertThat(specs)
                .as("more indexes are declared than §4 has rows for %s — §4's acceptance is set "
                        + "equality, so an extra declaration is drift, not diligence", prefix)
                .hasSize(expected.size());
    }

    /** Every declared index exists on the server with the options the registry gives it. */
    public void assertAllPresent(List<IndexSpec> specs) {
        List<String> problems = new ArrayList<>();

        for (IndexSpec spec : specs) {
            Document live = indexesOf(spec.collection()).stream()
                    .filter(index -> spec.name().equals(index.getString("name")))
                    .findFirst()
                    .orElse(null);

            if (live == null) {
                problems.add("%s.%s is absent".formatted(spec.collection(), spec.name()));
                continue;
            }
            List<String> declaredKeys = new ArrayList<>(spec.keys().keySet());

            if (spec.text()) {
                // A text index does not report the fields it covers under `key` — that holds
                // MongoDB's internal `_fts`/`_ftsx` pair regardless of the fields indexed. The
                // covered fields are the keys of `weights`, and order carries no meaning there.
                Document weights = live.get("weights", Document.class);
                if (weights == null || !weights.keySet().containsAll(declaredKeys)) {
                    problems.add("%s.%s covers %s; §4 declares %s".formatted(
                            spec.collection(), spec.name(),
                            weights == null ? "nothing" : weights.keySet(), declaredKeys));
                }
            } else {
                // Key ORDER, not just the key set. A compound index is usable as a prefix, so
                // { userId: 1, status: 1 } serves a query on userId alone and the reverse does
                // not. Comparing as sets would call the wrong index correct, and the query it
                // was built for would still be a collection scan.
                List<String> liveKeys = new ArrayList<>(live.get("key", Document.class).keySet());
                if (!liveKeys.equals(declaredKeys)) {
                    problems.add(("%s.%s has keys %s; §4 declares %s — a compound index in the "
                            + "wrong order does not serve the query it was written for")
                            .formatted(spec.collection(), spec.name(), liveKeys, declaredKeys));
                }
            }
            if (spec.unique() && !Boolean.TRUE.equals(live.getBoolean("unique"))) {
                problems.add("%s.%s exists but is NOT unique — the constraint is decorative"
                        .formatted(spec.collection(), spec.name()));
            }
            if (spec.sparse() && !Boolean.TRUE.equals(live.getBoolean("sparse"))) {
                problems.add(("%s.%s is not sparse; a unique index over a nullable field treats "
                        + "'missing' as a value, so the second document without one collides")
                        .formatted(spec.collection(), spec.name()));
            }
            if (spec.expireAfter() != null && !live.containsKey("expireAfterSeconds")) {
                problems.add("%s.%s has no expireAfterSeconds — nothing expires"
                        .formatted(spec.collection(), spec.name()));
            }
        }

        assertThat(problems)
                .as("ET-PLT-002 §4's index registry is not satisfied on the server")
                .isEmpty();
    }

    /**
     * Indexes on the server that the registry does not declare.
     *
     * <p>Returned rather than asserted: the goal is set <em>equality</em>, but several
     * live uniqueness constraints are real and simply absent from the registry. Deleting those
     * to reach equality would drop a constraint to satisfy a document. The caller ratchets.</p>
     */
    public List<String> undeclared(List<IndexSpec> specs) {
        Map<String, Set<String>> declared = specs.stream().collect(Collectors.groupingBy(
                IndexSpec::collection, Collectors.mapping(IndexSpec::name, Collectors.toSet())));

        List<String> extra = new ArrayList<>();
        declared.forEach((collection, names) -> indexesOf(collection).stream()
                .map(index -> index.getString("name"))
                .filter(name -> !"_id_".equals(name) && !names.contains(name))
                .forEach(name -> extra.add(collection + "." + name)));
        return extra;
    }

    /**
     * The property a unique index exists for: the server refuses the second one.
     *
     * <p>Clears the probe value first. The Testcontainers instance is reused between runs, so
     * the database is <em>not</em> empty at the start of a suite — a leftover probe document
     * makes the <b>first</b> insert collide, which reports as an error in the assertion rather
     * than as the constraint working.</p>
     */
    public void assertRefusesDuplicate(String collection, String field, Object value) {
        template.remove(
                        org.springframework.data.mongodb.core.query.Query.query(
                                org.springframework.data.mongodb.core.query.Criteria.where(field).is(value)),
                        collection)
                .block();

        template.insert(new Document(field, value), collection).block();

        // then(empty) rather than thenReturn(null): Reactor rejects a null value, and it does so
        // while the chain is being assembled — before onErrorResume is in a position to catch
        // anything — so the NullPointerException would surface instead of the duplicate-key
        // error this is trying to observe.
        Throwable failure = template.insert(new Document(field, value), collection)
                .then(Mono.<Throwable>empty())
                .onErrorResume(Mono::just)
                .block();

        assertThat(failure)
                .as("a second document with %s=%s was accepted — the unique index on %s is not "
                        + "enforcing anything, and the race §4 describes is open", field, value, collection)
                .isNotNull();
    }

    /**
     * {@code explain()} reports an index scan rather than a collection scan for this query.
     *
     * <p>A {@code COLLSCAN} on a hot query is not a slow page; at on-sale peak it is the
     * outage, and it is invisible in every environment small enough to test by hand.</p>
     */
    public void assertUsesIndex(String collection, Document filter) {
        Document explain = template.getMongoDatabase()
                .flatMap(database -> Mono.from(database.runCommand(new Document()
                        .append("explain", new Document("find", collection).append("filter", filter))
                        .append("verbosity", "queryPlanner"))))
                .block();

        assertThat(explain).as("explain returned nothing for %s", collection).isNotNull();

        Document winningPlan = explain
                .get("queryPlanner", Document.class)
                .get("winningPlan", Document.class);

        List<String> stages = stageNames(winningPlan);

        // Matched as a substring, not by equality. MongoDB 8 plans a single-field equality
        // lookup as EXPRESS_IXSCAN — a faster path, still an index scan — and pinning the exact
        // stage name would fail this test on a server upgrade that changed nothing about
        // whether the index is used.
        assertThat(stages)
                .as("query %s on %s planned as %s — §4 names this one of the hot queries, and a "
                        + "collection scan here only hurts under the load that makes it matter",
                        filter.toJson(), collection, stages)
                .anyMatch(stage -> stage.contains("IXSCAN"));

        assertThat(stages)
                .as("query %s on %s reached a collection scan", filter.toJson(), collection)
                .doesNotContain("COLLSCAN");
    }

    // --------------------------------------------------------------------- helpers

    private List<Document> indexesOf(String collection) {
        return template.getMongoDatabase()
                .flatMapMany(database -> Flux.from(database.getCollection(collection).listIndexes()))
                .collectList()
                .blockOptional()
                .orElse(List.of());
    }

    /** Walks the plan tree — the index scan is usually under a FETCH, or several. */
    private static List<String> stageNames(Document plan) {
        List<String> stages = new ArrayList<>();
        collectStages(plan, stages);
        return stages;
    }

    private static void collectStages(Object node, List<String> stages) {
        if (node instanceof Document document) {
            String stage = document.getString("stage");
            if (stage != null) {
                stages.add(stage);
            }
            document.values().forEach(value -> collectStages(value, stages));
        } else if (node instanceof List<?> list) {
            list.forEach(value -> collectStages(value, stages));
        }
    }
}
