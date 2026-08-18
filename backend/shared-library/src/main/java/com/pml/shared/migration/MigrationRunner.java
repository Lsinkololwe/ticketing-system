package com.pml.shared.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Runs a service's data migrations, in order, once. Something had to.
 *
 * <h2>The problem this exists to prevent</h2>
 * Migration services in this platform have a habit of being written, tested, and
 * then never called. Seven of booking's eight were {@code @Service} beans that
 * nothing invoked; identity's organization backfill was another; catalog's
 * reference-data bootstrapper was a third. Each compiled, each had a passing
 * integration test, and each had never once run against a database that was not
 * created by a test.
 *
 * <p>That is a particularly quiet failure because the tests are honest: they
 * prove the migration works <em>when run</em>. Nothing was testing that it ever
 * would be. Every service that owns migrations now extends this and is covered
 * by a test asserting no migration service in its package is missing from
 * {@link #steps()}.
 *
 * <h2>Order is not cosmetic</h2>
 * Several migrations move a collection and later ones rewrite fields inside the
 * destination. Running the status rewrite before the collection move leaves it
 * rewriting an empty collection and reporting a clean zero — the same output as
 * success. Subclasses return an ordered map and say what each step depends on.
 *
 * <h2>Failure stops the sequence</h2>
 * By default a failed migration aborts startup. A service whose data is
 * half-converted cannot read its own documents, so carrying on would mean
 * serving errors instead of refusing to start — and the second is much easier to
 * notice. Each service exposes a {@code *.migrations.fail-fast=false} escape for
 * the operator who has decided otherwise with the data in front of them.
 */
@Slf4j
public abstract class MigrationRunner {

    /** The ledger collection for this service. Naming it per service keeps histories separate. */
    protected abstract MigrationLedger ledger();

    /** Name → the call, in dependency order. */
    protected abstract Map<String, Supplier<Mono<?>>> steps();

    /** {@code <service>.migrations.enabled}. */
    protected abstract boolean isEnabled();

    /** {@code <service>.migrations.fail-fast}. */
    protected abstract boolean isFailFast();

    /** For logs — which service's migrations these are. */
    protected String serviceName() {
        return getClass().getSimpleName();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runMigrations() {
        if (!isEnabled()) {
            log.warn("{}: data migrations are DISABLED. If this environment has not been "
                    + "migrated, reads will fail.", serviceName());
            return;
        }

        Map<String, Supplier<Mono<?>>> steps = steps();
        log.info("{}: {} migration step(s) to consider", serviceName(), steps.size());

        Flux.fromIterable(steps.entrySet())
                // concatMap, not flatMap: the order is a dependency order, and
                // running these concurrently would race a collection move
                // against the rewrite that reads its destination.
                .concatMap(step -> runOnce(step.getKey(), step.getValue()))
                .then()
                .block();
    }

    private Mono<String> runOnce(String name, Supplier<Mono<?>> migration) {
        return ledger().find(name)
                .flatMap(existing -> switch (existing.status()) {
                    case SUCCEEDED -> {
                        log.debug("Migration '{}' already applied — skipping", name);
                        yield Mono.just(name);
                    }
                    // A previous run died mid-migration, or another instance is
                    // running it right now. Both are cases for a human: retrying
                    // automatically could double-apply a step that is not
                    // idempotent in the way its author assumed.
                    case RUNNING -> refuse(name, "left RUNNING by a previous attempt or another instance");
                    case FAILED -> refuse(name, "failed previously: " + existing.failure());
                })
                .switchIfEmpty(Mono.defer(() -> claimAndRun(name, migration)));
    }

    private Mono<String> refuse(String name, String why) {
        String message = "Migration '" + name + "' " + why
                + ". Refusing to continue: resolve it and clear its row in the migration ledger.";
        log.error(message);
        return isFailFast() ? Mono.error(new IllegalStateException(message)) : Mono.just(name);
    }

    private Mono<String> claimAndRun(String name, Supplier<Mono<?>> migration) {
        return ledger().claim(name)
                .flatMap(claimed -> {
                    if (!claimed) {
                        return Mono.just(name);
                    }
                    return Mono.defer(migration::get)
                            .map(Object::toString)
                            .defaultIfEmpty("(no detail reported)")
                            .flatMap(result -> ledger().succeed(name, result)
                                    .doOnSuccess(v -> log.info("Migration '{}' applied: {}", name, result))
                                    .thenReturn(name))
                            .onErrorResume(error -> ledger()
                                    .fail(name, error.getMessage())
                                    // Recorded before rethrowing, so the failure
                                    // survives the process exiting.
                                    .then(isFailFast()
                                            ? Mono.<String>error(new IllegalStateException(
                                                    "Migration '" + name + "' failed: " + error.getMessage(), error))
                                            : Mono.just(name)));
                });
    }

    /** Exposed for the integration tests, which need the same order production uses. */
    public List<String> stepNames() {
        return List.copyOf(steps().keySet());
    }
}
