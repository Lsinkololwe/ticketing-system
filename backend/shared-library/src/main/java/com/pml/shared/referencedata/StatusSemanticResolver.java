package com.pml.shared.referencedata;

import com.pml.shared.constants.WorkflowSemantic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Resolves an administrator-configured status code to the meaning code branches on.
 *
 * <h2>Why consumers read catalog's collection directly</h2>
 * Reference data is owned by catalog-service, and the usual answer would be a
 * GraphQL call. This is on the write path of every payout, ticket, escrow
 * and organization transition, so an HTTP round trip per write would put catalog-service in the
 * critical path of taking money — and make a catalog outage stop payments
 * rather than only stop editing dropdowns.
 *
 * <p>The platform runs one MongoDB with collection prefixes (see CLAUDE.md), so
 * this reads {@code reference_data} directly, READ-ONLY, and caches. Catalog
 * remains the only writer. That is a deliberate coupling on a shared datastore
 * rather than an accidental one: the alternative couples availability instead,
 * which is worse.
 *
 * <h2>The cache</h2>
 * Statuses change when an administrator edits them, which is rare, and a stale
 * entry for a few minutes is harmless — the semantic of an existing code does
 * not move. New codes are picked up because a miss always hits the database.
 *
 * <h2>Unknown codes return empty, never a default</h2>
 * A code with no semantic is exactly the condition this whole design exists to
 * prevent. Defaulting it to something plausible would hide the fault at the one
 * moment it is cheap to catch.
 *
 * <h2>Why the template arrives lazily</h2>
 * Consumers wire this into Spring Data entity callbacks, and callbacks are
 * collected while {@code ReactiveMongoTemplate} itself is being constructed.
 * Taking the template as a constructor argument therefore closes a cycle —
 * template needs callback needs resolver needs template — and the whole
 * application context fails to start. An {@link ObjectProvider} defers the
 * lookup to first use, by which point the template exists.
 */
@Slf4j
public class StatusSemanticResolver {

    /**
     * The registered collection name, {@code catalog_reference_data}. It is a literal rather than
     * {@code CatalogCollections.REFERENCE_DATA} because shared-library is a leaf and may not
     * import from a service — which is itself the argument for moving this class into the
     * reference data engine, where the constant is in scope and this string cannot
     * drift away from the registry unnoticed.
     */
    private static final String COLLECTION = "catalog_reference_data";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final Supplier<ReactiveMongoTemplate> mongoTemplate;

    /**
     * The platform clock, not {@code System.currentTimeMillis()}.
     *
     * <p>This one is a testability fix with teeth. The cache holds a 15-minute TTL, and the only
     * ways to assert expiry against the wall clock are to sleep for a quarter of an hour or to
     * trust that the arithmetic is right. So the behaviour that matters — a stale semantic is
     * re-read rather than served forever — had no test, in a cache whose entries decide how a
     * status is interpreted across three services.</p>
     */
    private final Clock clock;

    @Autowired
    public StatusSemanticResolver(ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider,
                                  Clock clock) {
        this.mongoTemplate = mongoTemplateProvider::getObject;
        this.clock = clock;
    }

    /** Direct construction, for tests that build a template themselves. */
    public StatusSemanticResolver(ReactiveMongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = () -> mongoTemplate;
        this.clock = clock;
    }

    private record Entry(WorkflowSemantic semantic, long expiresAtMillis) {}

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    /**
     * @param type the reference type, e.g. {@code PAYOUT_STATUS}
     * @param code the stored status code
     * @return the semantic, or empty when the code is unknown
     */
    public Mono<WorkflowSemantic> resolve(String type, String code) {
        if (type == null || code == null || code.isBlank()) {
            return Mono.empty();
        }

        String key = type + "/" + code;
        Entry cached = cache.get(key);
        if (cached != null && cached.expiresAtMillis() > clock.millis()) {
            return Mono.justOrEmpty(cached.semantic());
        }

        // Not filtered by isActive: a retired status still appears on the rows
        // that reached it before it was retired, and those must keep resolving.
        Query query = Query.query(Criteria.where("type").is(type).and("code").is(code));

        return mongoTemplate.get()
                .findOne(query, org.bson.Document.class, COLLECTION)
                .mapNotNull(doc -> doc.getString("semantic"))
                .map(WorkflowSemantic::valueOf)
                .doOnNext(semantic -> cache.put(key,
                        new Entry(semantic, clock.millis() + TTL.toMillis())))
                .doOnError(e -> log.warn("Could not resolve {}.{}: {}", type, code, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /** Clears the cache. Used by tests and after a known reference-data change. */
    public void invalidate() {
        cache.clear();
    }
}
