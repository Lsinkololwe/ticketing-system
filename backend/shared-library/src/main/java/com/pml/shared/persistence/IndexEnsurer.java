package com.pml.shared.persistence;

import com.mongodb.client.model.IndexOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Creates the declared indexes, and reports what it could not.
 *
 * <h2>Error 85, and why each index is created independently</h2>
 * MongoDB rejects a second index over identical keys under a different name with
 * {@code IndexOptionsConflict} (85). Creating indexes as one batch ({@code Mono.when}) would let a
 * single conflict fail the whole batch and abort start-up. Each index here is attempted on its own
 * and a conflict is recorded rather than thrown, so one collision cannot stop the rest from being
 * created.
 *
 * <h2>A conflict is reported, not swallowed</h2>
 * The distinction matters: "already exists, identically" is success, and "exists with different
 * options" means the live index is not the one declared — most consequentially, a unique
 * index that is not actually unique. The second is returned in the report so a test can fail on
 * it, because at runtime it is invisible.
 */
@Slf4j
public final class IndexEnsurer {

    public enum Status { CREATED, ALREADY_PRESENT, CONFLICT }

    public record Outcome(IndexSpec spec, Status status, String detail) {
    }

    public record Report(List<Outcome> outcomes) {

        public List<Outcome> conflicts() {
            return outcomes.stream().filter(o -> o.status() == Status.CONFLICT).toList();
        }

        public boolean isClean() {
            return conflicts().isEmpty();
        }

        @Override
        public String toString() {
            long created = outcomes.stream().filter(o -> o.status() == Status.CREATED).count();
            String summary = "%d created, %d already present, %d conflicting".formatted(
                    created,
                    outcomes.stream().filter(o -> o.status() == Status.ALREADY_PRESENT).count(),
                    conflicts().size());
            return isClean() ? summary : summary + " — " + conflicts().stream()
                    .map(o -> o.spec().collection() + "." + o.spec().name() + ": " + o.detail())
                    .toList();
        }
    }

    private final ReactiveMongoTemplate mongoTemplate;

    public IndexEnsurer(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Mono<Report> ensure(List<IndexSpec> specs) {
        return Flux.fromIterable(specs)
                .concatMap(this::ensureOne)
                .collectList()
                .map(outcomes -> {
                    Report report = new Report(List.copyOf(outcomes));
                    if (report.isClean()) {
                        log.info("Indexes: {}", report);
                    } else {
                        log.error("Indexes: {}", report);
                    }
                    return report;
                });
    }

    private Mono<Outcome> ensureOne(IndexSpec spec) {
        IndexOptions options = new IndexOptions().name(spec.name());
        if (spec.unique()) {
            options.unique(true);
        }
        if (spec.sparse()) {
            options.sparse(true);
        }
        if (spec.expireAfter() != null) {
            options.expireAfter(spec.expireAfter().toSeconds(), TimeUnit.SECONDS);
        }
        if (spec.partialFilter() != null) {
            options.partialFilterExpression(spec.partialFilter());
        }
        if (spec.text()) {
            options.weights(spec.weights());
        }

        return mongoTemplate.getMongoDatabase()
                .flatMap(database -> Mono.from(
                        database.getCollection(spec.collection())
                                .createIndex(spec.keyDocument(), options)))
                .map(created -> new Outcome(spec, Status.CREATED, created))
                // createIndex is idempotent for an identical definition — it returns the name
                // rather than erroring — so reaching here means the live index differs from
                // what was declared, or the server refused. Both are CONFLICT; the message
                // says which, and a test fails on either.
                .onErrorResume(error -> Mono.just(
                        new Outcome(spec, Status.CONFLICT, String.valueOf(error.getMessage()))));
    }
}
