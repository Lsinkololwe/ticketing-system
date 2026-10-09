package com.pml.catalog.config;

import com.pml.shared.persistence.IndexSpec;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.catalog.persistence.CatalogCollections;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.catalog.domain.model.ReferenceData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import com.pml.catalog.service.referencedata.ReferenceDataBootstrapper;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * ReferenceDataSeeder — seeds the {@code reference_data} collection at boot from
 * {@code resources/seed/reference-data.json}.
 *
 * <p>Idempotent and re-runnable: each seed row is matched on {@code (type, code)} and upserted as a
 * {@code isSystem: true} row. System-owned fields (name, description, hierarchy, order, metadata)
 * are refreshed from the seed file on every boot — the seed file is the source of truth for system
 * rows. The {@code isActive} flag and audit-created fields are written on insert only, so an admin
 * deactivating a system row survives restarts, and admin-created (non-system) rows are never touched.</p>
 *
 * <p>This is why nothing needs to be hardcoded in Java: operators, banks, currencies, genres, reason
 * codes and the rest all live as data, seeded here and editable through the admin API.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenceDataSeeder implements ApplicationRunner {

    private static final String COLLECTION = CatalogCollections.REFERENCE_DATA;
    private static final String CLASS_ALIAS = ReferenceData.class.getName();
    private static final String SYSTEM_ACTOR = "system";

    private final ReactiveMongoTemplate mongoTemplate;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;
    private final ObjectMapper objectMapper;
    private final ReferenceDataBootstrapper bootstrapper;

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows = loadSeed();
        if (rows.isEmpty()) {
            log.warn("Reference data seed file is empty or missing — skipping seed");
            return;
        }

        Instant now = clock.instant();
        // Indexes first — the unique (type, code) index is the data-integrity backbone, and the
        // upserts below rely on it. The registry declares them; this start-up step may run before
        // the registry's own pass, so it ensures the reference-data subset itself.
        Long upserted = ensureIndexes()
                .thenMany(Flux.fromIterable(rows).flatMap(row -> upsert(row, now)))
                .count()
                .block();

        log.info("Reference data seed complete: {} system rows ensured across the catalog", upserted);

        bootstrapStatusEnums();
    }

    /**
     * Materialise the status enums as reference data.
     *
     * <h2>Why this call is here and not in the bootstrapper</h2>
     * {@link ReferenceDataBootstrapper} was written to run "on every start",
     * documented itself as idempotent and safe to do so, and had no caller
     * anywhere in production — only tests, which invoked it directly and passed.
     * The consequence was invisible: every workflow status type
     * ({@code TICKET_STATUS}, {@code PAYOUT_STATUS}, {@code PAYMENT_STATUS} …)
     * was simply absent from {@code reference_data}, so the semantic resolver had
     * nothing to resolve against and the admin screens had nothing to configure.
     *
     * <p>It runs after the file seed rather than before it because the
     * bootstrapper's whole "already exists" contract rests on the unique
     * {@code (type, code)} index, and {@link #ensureIndexes()} above is what
     * guarantees it exists before either runs.
     *
     * <p>A failure here aborts startup. The bootstrapper refuses to guess at a
     * missing {@code WorkflowSemantic}, and a service that starts without one is
     * a service routing a status to the wrong branch.
     */
    private void bootstrapStatusEnums() {
        ReferenceDataBootstrapper.Result result = bootstrapper.bootstrap().block();
        if (result != null) {
            log.info("Reference data bootstrap: {} status rows inserted, {} already configured",
                    result.inserted(), result.retained());
        }
    }

    /**
     * Ensure the collection's indexes exist. Idempotent — {@code ensureIndex} is a no-op when the
     * index is already present. The unique {@code (type, code)} index enforces one code per type at
     * the database level, independent of any application-layer check.
     */
    private Mono<Void> ensureIndexes() {
        List<IndexSpec> referenceData = CatalogIndexInitializer.specifications().stream()
                .filter(spec -> COLLECTION.equals(spec.collection()))
                .toList();
        return new IndexEnsurer(mongoTemplate).ensure(referenceData)
                .flatMap(report -> report.isClean()
                        ? Mono.<Void>empty()
                        : Mono.error(new IllegalStateException("reference_data indexes could not be created: " + report)));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadSeed() {
        try (InputStream in = new ClassPathResource("seed/reference-data.json").getInputStream()) {
            return objectMapper.readValue(in, List.class);
        } catch (Exception e) {
            log.error("Failed to read reference-data seed file: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private reactor.core.publisher.Mono<?> upsert(Map<String, Object> row, Instant now) {
        String type = (String) row.get("type");
        String code = (String) row.get("code");
        if (type == null || code == null) {
            log.warn("Skipping seed row without type/code: {}", row);
            return reactor.core.publisher.Mono.empty();
        }

        Query query = Query.query(Criteria.where("type").is(type).and("code").is(code));

        Update update = new Update()
                // Refreshed from the seed file on every boot (seed is source of truth for system rows)
                .set("type", type)
                .set("code", code)
                .set("name", row.get("name"))
                .set("description", row.get("description"))
                .set("parentType", row.get("parentType"))
                .set("parentCode", row.get("parentCode"))
                .set("displayOrder", ((Number) row.getOrDefault("displayOrder", 0)).intValue())
                .set("metadata", row.getOrDefault("metadata", Map.of()))
                .set("isSystem", true)
                .set("updatedAt", now)
                .set("updatedBy", SYSTEM_ACTOR)
                // Insert-only: preserve admin active/inactive toggles and original create audit
                .setOnInsert("isActive", true)
                .setOnInsert("createdAt", now)
                .setOnInsert("createdBy", SYSTEM_ACTOR)
                .setOnInsert("_class", CLASS_ALIAS);

        return mongoTemplate.upsert(query, update, COLLECTION)
                .doOnError(e -> log.error("Seed upsert failed for {}:{} — {}", type, code, e.getMessage()));
    }
}
