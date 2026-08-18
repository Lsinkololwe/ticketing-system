package com.pml.identity.migration;

import com.pml.identity.domain.model.Organization;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.shared.referencedata.StatusSemanticResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Stamps {@code statusSemantic} onto organizations written before the field existed.
 *
 * <h2>Why this is needed</h2>
 * The admin review queue and every "is this organization operational" check now
 * read the semantic rather than enumerating status codes, because administrators
 * own the code set. Rows written before the field existed carry null and match no
 * semantic query — so without this backfill the review queue would appear empty
 * while applications sit in it, which reads as "nothing to do" rather than as a
 * fault.
 *
 * <h2>Idempotency</h2>
 * Only rows with a null semantic are touched. It does not overwrite an existing
 * value: a stamped row records what was true when it was written, and re-deriving
 * from today's reference data would rewrite history if an administrator has since
 * re-classified a status. The write-path stamper is the opposite by design — it
 * fires when the status is actually changing, so re-deriving there is correct.
 *
 * <h2>Unresolvable rows are reported, not guessed</h2>
 * An organization filed under the wrong meaning is worse than one filed under
 * none. {@code PENDING_DELETION} guessed as {@code PENDING} would put an account
 * on its way off the platform into the approval inbox.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationStatusSemanticMigrationService {

    private static final String ORGANIZATION_TYPE = "ORGANIZATION_STATUS";

    private final OrganizationRepository organizationRepository;
    private final StatusSemanticResolver semanticResolver;

    /**
     * @param stamped      rows given a semantic by this run
     * @param alreadySet   rows that already had one
     * @param unresolvable rows whose status code matches no reference data
     */
    public record Result(long stamped, long alreadySet, long unresolvable) {
        public long examined() {
            return stamped + alreadySet + unresolvable;
        }

        static Result accumulate(Result acc, Outcome outcome) {
            return switch (outcome) {
                case STAMPED -> new Result(acc.stamped + 1, acc.alreadySet, acc.unresolvable);
                case ALREADY_SET -> new Result(acc.stamped, acc.alreadySet + 1, acc.unresolvable);
                case UNRESOLVABLE -> new Result(acc.stamped, acc.alreadySet, acc.unresolvable + 1);
            };
        }
    }

    private enum Outcome { STAMPED, ALREADY_SET, UNRESOLVABLE }

    public Mono<Result> migrate() {
        return organizationRepository.findAll()
                // Sequential: a backfill competes with live traffic for the same
                // connection pool, and finishing later beats slowing down a
                // signup someone is waiting on.
                .concatMap(this::migrateOne)
                .reduce(new Result(0, 0, 0), Result::accumulate)
                .doOnSuccess(r -> log.info(
                        "Organization status semantic backfill: {} stamped, {} already set, {} unresolvable (of {})",
                        r.stamped(), r.alreadySet(), r.unresolvable(), r.examined()));
    }

    private Mono<Outcome> migrateOne(Organization organization) {
        if (organization.getStatusSemantic() != null) {
            return Mono.just(Outcome.ALREADY_SET);
        }
        if (organization.getStatus() == null) {
            return Mono.just(Outcome.UNRESOLVABLE);
        }
        return semanticResolver.resolve(ORGANIZATION_TYPE, organization.getStatus().name())
                .flatMap(semantic -> {
                    organization.setStatusSemantic(semantic);
                    return organizationRepository.save(organization).thenReturn(Outcome.STAMPED);
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    log.warn("Organization {} has status {} with no reference data — left unstamped",
                            organization.getId(), organization.getStatus());
                    return Outcome.UNRESOLVABLE;
                }));
    }
}
