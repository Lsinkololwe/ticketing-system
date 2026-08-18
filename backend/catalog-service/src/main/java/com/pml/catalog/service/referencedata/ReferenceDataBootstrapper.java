package com.pml.catalog.service.referencedata;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.shared.constants.WorkflowSemantic;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.repository.ReferenceDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Materialises the code's status enums as configurable reference data.
 *
 * <h2>No seed file</h2>
 * Reflects over every enum registered in {@link ReferenceDataSource} and inserts
 * one row per constant. Nobody writes a list, so nobody can forget to update
 * one: adding a constant to {@code TicketStatus} makes it appear as configurable
 * data on the next start.
 *
 * <h2>It inserts; it never updates</h2>
 * A row that already exists is left exactly as it is. That is the contract with
 * the administrator — they rename {@code PENDING} to "Awaiting review", pick a
 * colour, reorder the list, and a deployment does not undo any of it. The unique
 * {@code (type, code)} index decides what "already exists" means, so two
 * instances starting together cannot both insert.
 *
 * <h2>It fails loudly rather than guessing</h2>
 * A workflow constant with no declared {@link WorkflowSemantic} aborts the run.
 * Defaulting it would produce a status that looks configured, reads correctly in
 * the admin screen, and routes behaviour to the wrong branch — found weeks later
 * by someone whose payout never moved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferenceDataBootstrapper {

    private final ReferenceDataRepository repository;

    /**
     * @param inserted rows created by this run
     * @param retained rows already present and left untouched
     */
    public record Result(long inserted, long retained) {
        public long total() {
            return inserted + retained;
        }
    }

    /** Idempotent, so it is safe on every boot rather than a one-off migration. */
    public Mono<Result> bootstrap() {
        // Force the registrations' static initialiser BEFORE reading the
        // registry, or an empty registry reads as "nothing to seed" instead of
        // "not loaded yet" — a silent no-op rather than an error.
        ReferenceDataRegistrations.ensureRegistered();

        Map<ReferenceType, ReferenceDataSource.Registration> registry = ReferenceDataSource.registry();
        if (registry.isEmpty()) {
            return Mono.just(new Result(0, 0));
        }

        return Flux.fromIterable(registry.values())
                .concatMap(this::seed)
                .reduce(new Result(0, 0), (acc, r) ->
                        new Result(acc.inserted() + r.inserted(), acc.retained() + r.retained()))
                .flatMap(result -> normaliseTransitions().thenReturn(result))
                .doOnSuccess(r -> log.info(
                        "Reference data bootstrap: {} inserted, {} already configured",
                        r.inserted(), r.retained()));
    }

    /**
     * Give every row an {@code allowedTransitions} array, even an empty one.
     *
     * <h2>Why this is here and not a ledgered migration</h2>
     * Rows seeded before the field existed carry no {@code allowedTransitions}
     * key at all — 130 of them in the development database. Nothing fails today
     * because every reader null-checks, but the SDL declares the field
     * {@code [String!]!} and the model declares it a list, so the stored shape
     * disagrees with both. The first reader that trusts either one gets a null
     * pointer against data that looks perfectly ordinary.
     *
     * <p>It belongs to seeding rather than to a migration runner because it is
     * idempotent, unconditional, and about the shape this class writes — the
     * same reason the bootstrap itself runs on every boot instead of once. A
     * ledger would record it as applied and then skip the next batch of rows
     * that arrive without the field.
     *
     * <p>The filter is {@code $exists: false}, so a row that has the field —
     * empty or populated — is never touched. An administrator's configured
     * transitions are safe.
     */
    private Mono<Long> normaliseTransitions() {
        return repository.normaliseMissingTransitions()
                .doOnNext(count -> {
                    if (count > 0) {
                        log.info("Reference data bootstrap: gave {} row(s) an empty "
                                + "allowedTransitions array", count);
                    }
                });
    }

    private Mono<Result> seed(ReferenceDataSource.Registration registration) {
        List<ReferenceData> rows;
        try {
            rows = derive(registration);
        } catch (IllegalStateException e) {
            return Mono.error(e);
        }
        return Flux.fromIterable(rows)
                // Sequential: small lists, and a duplicate-key on one row must
                // not cancel the rest of the type.
                .concatMap(this::insertIfAbsent)
                .reduce(new Result(0, 0), (acc, inserted) -> inserted
                        ? new Result(acc.inserted() + 1, acc.retained())
                        : new Result(acc.inserted(), acc.retained() + 1));
    }

    /** True when this run created the row; false when it was already there. */
    private Mono<Boolean> insertIfAbsent(ReferenceData row) {
        return repository.insert(row)
                .thenReturn(true)
                // The index decides, not a prior read: two instances booting
                // together would both pass a read-then-write.
                .onErrorReturn(DuplicateKeyException.class, false);
    }

    private List<ReferenceData> derive(ReferenceDataSource.Registration registration) {
        Enum<?>[] constants = registration.enumClass().getEnumConstants();
        List<ReferenceData> rows = new ArrayList<>(constants.length);
        LocalDateTime now = LocalDateTime.now();

        int order = 0;
        for (Enum<?> constant : constants) {
            String code = constant.name();
            WorkflowSemantic semantic = registration.semantics().get(code);

            if (registration.type().isWorkflow() && semantic == null) {
                throw new IllegalStateException(
                        "Reference data bootstrap: " + registration.type() + "." + code
                                + " has no declared WorkflowSemantic. Map it in "
                                + "ReferenceDataRegistrations, or code that branches on this "
                                + "status will not recognise it.");
            }

            rows.add(ReferenceData.builder()
                    .type(registration.type())
                    .code(code)
                    .name(ReferenceDataSource.humanize(code))
                    .semantic(semantic)
                    // Seeded rows are protected: renameable, recolourable and
                    // retirable, but not deletable, because a code path resolves
                    // behaviour through them.
                    .isSystem(true)
                    .isActive(true)
                    .displayOrder(order += 10)
                    .createdAt(now)
                    .updatedAt(now)
                    .createdBy("system")
                    .build());
        }
        return rows;
    }
}
