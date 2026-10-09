package com.pml.catalog.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.service.referencedata.CountryCatalogue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A versioned release of reference data: the real configuration the platform runs on.
 *
 * <p>This is configuration, not mock data. Each version is a classpath file under {@code seed/}
 * naming the rows it ships, the rows it retires, and the generated sets it adds (the country list,
 * which comes from libphonenumber rather than a file). The runner records each version once in the
 * migration ledger, so a version applies once per database and an administrator's later edits to a
 * row are never overwritten by a restart. A later correction is a new version under a new step
 * name, never an edit of an applied one.
 *
 * <h2>Idempotent</h2>
 * Every row is upserted on {@code (type, code)}; {@code isActive} and the create audit are written
 * on insert only, so applying a version to a database that already holds its rows changes nothing
 * but the system-owned fields it names. Running it twice yields the same collection.
 *
 * <h2>Retiring is deactivating</h2>
 * ET-PLT-014-R7: a row is never deleted. A retired code is set inactive so records that referenced
 * it keep resolving, and it drops out of every picker.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferenceDataSeedMigrationService {

    static final String SYSTEM_ACTOR = "system";
    private static final String CLASS_ALIAS = com.pml.catalog.domain.model.ReferenceData.class.getName();

    private final ReactiveMongoTemplate mongoTemplate;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    /**
     * The collection validators, applied before any row is written. Migrations run ahead of the validator
     * runner, so on a database built before a release a new reference type would be refused by the old
     * validator it has not yet replaced.
     */
    private final com.pml.catalog.config.MongoSchemaValidationConfig validation;

    /** Version 2: onboarding, access, communication and reporting vocabularies, the districts, the countries. */
    public Mono<String> applyV2() {
        return apply("seed/reference-data-v2.json");
    }

    /** Applies one seed file. Public so a test can apply it twice against a real database. */
    @SuppressWarnings("unchecked")
    public Mono<String> apply(String resource) {
        Map<String, Object> seed;
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            seed = objectMapper.readValue(in, Map.class);
        } catch (Exception e) {
            return Mono.error(new IllegalStateException("Reference data seed " + resource + " unreadable: " + e.getMessage(), e));
        }
        List<Map<String, Object>> rows = new ArrayList<>((List<Map<String, Object>>) seed.getOrDefault("rows", List.of()));
        if (((List<String>) seed.getOrDefault("generated", List.of())).contains("COUNTRY")) {
            rows.addAll(CountryCatalogue.rows());
        }
        List<Map<String, Object>> retire = (List<Map<String, Object>>) seed.getOrDefault("retire", List.of());
        Instant now = clock.instant();
        int ensured = rows.size();
        Object version = seed.get("version");
        return Mono.<Void>fromRunnable(validation::applySchemaValidation)
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .then(Flux.fromIterable(rows).concatMap(row -> upsert(row, now)).then())
                .then(Flux.fromIterable(retire).concatMap(this::deactivate).reduce(0L, Long::sum))
                .map(retired -> "version " + version + ": " + ensured + " rows ensured, "
                        + retired + " retired");
    }

    private Mono<Object> upsert(Map<String, Object> row, Instant now) {
        String type = (String) row.get("type");
        String code = (String) row.get("code");
        Query query = Query.query(Criteria.where("type").is(type).and("code").is(code));
        Update update = new Update()
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
                .setOnInsert("isActive", true)
                .setOnInsert("createdAt", now)
                .setOnInsert("createdBy", SYSTEM_ACTOR)
                .setOnInsert("_class", CLASS_ALIAS);
        return mongoTemplate.upsert(query, update, CatalogCollections.REFERENCE_DATA).map(result -> (Object) result);
    }

    private Mono<Long> deactivate(Map<String, Object> row) {
        Query query = Query.query(Criteria.where("type").is(row.get("type")).and("code").is(row.get("code"))
                .and("isSystem").is(true).and("isActive").is(true));
        return mongoTemplate.updateFirst(query,
                        new Update().set("isActive", false).set("updatedAt", clock.instant()).set("updatedBy", SYSTEM_ACTOR),
                        CatalogCollections.REFERENCE_DATA)
                .map(result -> result.getModifiedCount());
    }
}
