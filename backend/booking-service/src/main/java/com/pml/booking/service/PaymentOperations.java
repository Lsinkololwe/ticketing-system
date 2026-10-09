package com.pml.booking.service;

import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.enums.PaymentAttemptType;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.PaymentAttemptFilterInput;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.error.ValidationRefusal;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The operator's view of payment attempts: search, what is stuck, and re-driving a collection by asking
 * the provider what really happened.
 *
 * <p>Nothing here decides an outcome. {@code resume} re-queries the provider and applies the answer through
 * the same path the webhook and the poll use, so an operator cannot make a payment succeed that the
 * provider says did not.
 */
@Service
public class PaymentOperations {

    public static final Set<PaymentAttemptStatus> IN_FLIGHT = Set.of(
            PaymentAttemptStatus.CREATED, PaymentAttemptStatus.PENDING_APPROVAL,
            PaymentAttemptStatus.PROCESSING, PaymentAttemptStatus.CONFIRMED);

    /** Failure codes that mean the provider was unreachable, so asking again is safe. */
    static final Set<String> BULK_SAFE_FAILURES = Set.of("PROVIDER_UNAVAILABLE", "PROVIDER_TIMEOUT", "NETWORK_ERROR",
            "CIRCUIT_BREAKER_OPEN");

    public static final int BULK_LIMIT = 50;
    public static final int DEFAULT_STUCK_MINUTES = 30;

    private static final Set<String> SORTABLE = Set.of("createdAt", "amount", "status", "riskScore", "updatedAt");
    private static final List<PaymentAttemptType> COLLECTIONS = Arrays.asList(PaymentAttemptType.COLLECT, null);

    private final ReactiveMongoTemplate template;
    private final PaymentOutcomeService outcomes;
    private final PaymentRiskService risk;
    private final Clock clock;

    public PaymentOperations(ReactiveMongoTemplate template, PaymentOutcomeService outcomes, PaymentRiskService risk, Clock clock) {
        this.template = template;
        this.outcomes = outcomes;
        this.risk = risk;
        this.clock = clock;
    }

    public record Outcome(String depositId, String result, String detail) {
    }

    // ---- search --------------------------------------------------------------------------------

    public Mono<Pages.Slice<PaymentAttempt>> search(PaymentAttemptFilterInput filter, OffsetPaginationInput pagination) {
        List<FieldViolation> violations = check(filter);
        if (!violations.isEmpty()) {
            return Mono.error(new ValidationRefusal(violations));
        }
        List<Criteria> all = conditions(filter);
        Criteria criteria = all.isEmpty() ? new Criteria() : new Criteria().andOperator(all);
        return Pages.offset(template, criteria, pagination, PaymentAttempt.class, SORTABLE, "createdAt")
                .flatMap(slice -> risk.assessAll(slice.data()).collectList()
                        .map(assessed -> new Pages.Slice<>(assessed, slice.pagination())));
    }

    /** Collections that started more than {@code minutes} ago and have not been fulfilled. */
    public Mono<Pages.Slice<PaymentAttempt>> stuck(Integer minutes, OffsetPaginationInput pagination) {
        int age = minutes == null ? DEFAULT_STUCK_MINUTES : minutes;
        if (age < 1 || age > 60 * 24 * 30) {
            return Mono.error(new ValidationRefusal(List.of(new FieldViolation("minutes", "must be between 1 minute and 30 days"))));
        }
        return Pages.offset(template, stuckCriteria(age), pagination, PaymentAttempt.class, SORTABLE, "createdAt");
    }

    Criteria stuckCriteria(int minutes) {
        return new Criteria().andOperator(
                Criteria.where("attemptType").in(COLLECTIONS),
                Criteria.where("status").in(IN_FLIGHT),
                Criteria.where("fulfilled").ne(true),
                Criteria.where("createdAt").lte(clock.instant().minus(Duration.ofMinutes(minutes))));
    }

    static List<FieldViolation> check(PaymentAttemptFilterInput filter) {
        List<FieldViolation> violations = new ArrayList<>();
        if (filter == null) {
            return violations;
        }
        if (filter.riskLevel() != null && !Set.of("LOW", "MEDIUM", "HIGH").contains(filter.riskLevel())) {
            violations.add(new FieldViolation("filter.riskLevel", "must be LOW, MEDIUM or HIGH"));
        }
        if (filter.createdAfter() != null && filter.createdBefore() != null && filter.createdAfter().isAfter(filter.createdBefore())) {
            violations.add(new FieldViolation("filter.createdBefore", "must not be before createdAfter"));
        }
        if (filter.minAmount() != null && filter.maxAmount() != null && filter.minAmount().compareTo(filter.maxAmount()) > 0) {
            violations.add(new FieldViolation("filter.maxAmount", "must not be below minAmount"));
        }
        if (filter.stuckForMinutes() != null && filter.stuckForMinutes() < 1) {
            violations.add(new FieldViolation("filter.stuckForMinutes", "must be at least 1"));
        }
        return violations;
    }

    List<Criteria> conditions(PaymentAttemptFilterInput filter) {
        List<Criteria> conditions = new ArrayList<>();
        if (filter == null) {
            return conditions;
        }
        if (filter.statuses() != null && !filter.statuses().isEmpty()) conditions.add(Criteria.where("status").in(filter.statuses()));
        if (filter.attemptType() != null) {
            conditions.add(filter.attemptType() == PaymentAttemptType.COLLECT
                    ? Criteria.where("attemptType").in(COLLECTIONS)
                    : Criteria.where("attemptType").is(filter.attemptType()));
        }
        if (filter.provider() != null) conditions.add(Criteria.where("provider").is(filter.provider()));
        if (filter.eventId() != null) conditions.add(Criteria.where("eventId").is(filter.eventId()));
        if (filter.organizationId() != null) conditions.add(Criteria.where("organizationId").is(filter.organizationId()));
        if (filter.buyerId() != null) conditions.add(Criteria.where("buyerId").is(filter.buyerId()));
        if (filter.reviewStatus() != null) conditions.add(Criteria.where("reviewStatus").is(filter.reviewStatus()));
        if (filter.riskLevel() != null) conditions.add(Criteria.where("riskLevel").is(filter.riskLevel()));
        if (filter.createdAfter() != null || filter.createdBefore() != null) {
            Criteria created = Criteria.where("createdAt");
            if (filter.createdAfter() != null) created.gte(filter.createdAfter());
            if (filter.createdBefore() != null) created.lte(filter.createdBefore());
            conditions.add(created);
        }
        if (filter.minAmount() != null || filter.maxAmount() != null) {
            Criteria amount = Criteria.where("amount");
            if (filter.minAmount() != null) amount.gte(filter.minAmount());
            if (filter.maxAmount() != null) amount.lte(filter.maxAmount());
            conditions.add(amount);
        }
        if (filter.reference() != null && !filter.reference().isBlank()) {
            String reference = filter.reference().trim();
            conditions.add(new Criteria().orOperator(Criteria.where("depositId").is(reference),
                    Criteria.where("attemptNumber").is(reference), Criteria.where("providerReference").is(reference),
                    Criteria.where("clientReferenceId").is(reference)));
        }
        if (filter.stuckForMinutes() != null) {
            conditions.add(stuckCriteria(filter.stuckForMinutes()));
        }
        return conditions;
    }

    // ---- recovery ------------------------------------------------------------------------------

    /** Asks the provider about one collection and applies the answer; the attempt as it stands afterwards. */
    public Mono<PaymentAttempt> resume(String depositId) {
        return template.findOne(Query.query(Criteria.where("depositId").is(depositId)), PaymentAttempt.class)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.TRANSACTION_NOT_RECOVERABLE, "no payment attempt " + depositId)))
                .flatMap(attempt -> {
                    if (attempt.getAttemptType() != null && attempt.getAttemptType() != PaymentAttemptType.COLLECT) {
                        return Mono.<PaymentAttempt>error(new TranslatedRefusal(ErrorCode.TRANSACTION_NOT_RECOVERABLE,
                                "only collections are resumed here", Map.of("currentStatus", String.valueOf(attempt.getAttemptType()))));
                    }
                    if (attempt.getStatus() == PaymentAttemptStatus.COMPLETED) {
                        return Mono.<PaymentAttempt>error(new TranslatedRefusal(ErrorCode.TRANSACTION_NOT_RECOVERABLE,
                                "the payment is already complete", Map.of("currentStatus", "COMPLETED")));
                    }
                    return outcomes.verifyAndApply(depositId)
                            .then(attempt.getReservationId() == null ? Mono.<Void>empty() : outcomes.resume(attempt.getReservationId()))
                            .then(template.findOne(Query.query(Criteria.where("depositId").is(depositId)), PaymentAttempt.class))
                            .flatMap(risk::assess);
                });
    }

    /**
     * Asks again about a batch of collections whose last answer was "could not reach the provider" or none at
     * all. A collection the provider declined, or whose amount did not match, is skipped: asking again
     * cannot change an answer the provider gave, and retrying it in bulk is how a decline gets argued with.
     */
    public Mono<List<Outcome>> retryMany(List<String> depositIds) {
        if (depositIds == null || depositIds.isEmpty() || depositIds.size() > BULK_LIMIT) {
            return Mono.error(new ValidationRefusal(List.of(new FieldViolation("depositIds",
                    "name between 1 and " + BULK_LIMIT + " payments"))));
        }
        return Flux.fromIterable(depositIds.stream().distinct().toList())
                .concatMap(depositId -> template.findOne(Query.query(Criteria.where("depositId").is(depositId)), PaymentAttempt.class)
                        .map(attempt -> safeToRetry(attempt)
                                ? resume(depositId)
                                        .map(after -> new Outcome(depositId, "RETRIED", String.valueOf(after.getStatus())))
                                        .onErrorResume(failure -> Mono.just(new Outcome(depositId, "FAILED", failure.getMessage())))
                                : Mono.just(new Outcome(depositId, "SKIPPED", "the provider's answer for this payment cannot change")))
                        .flatMap(outcome -> outcome)
                        .switchIfEmpty(Mono.just(new Outcome(depositId, "NOT_FOUND", "no payment attempt"))))
                .collectList();
    }

    static boolean safeToRetry(PaymentAttempt attempt) {
        if (attempt.getAttemptType() != null && attempt.getAttemptType() != PaymentAttemptType.COLLECT) {
            return false;
        }
        PaymentAttemptStatus status = attempt.getStatus();
        if (status == PaymentAttemptStatus.PENDING_APPROVAL || status == PaymentAttemptStatus.PROCESSING
                || status == PaymentAttemptStatus.CREATED || (status == PaymentAttemptStatus.CONFIRMED && !attempt.isFulfilled())) {
            return true;
        }
        return (status == PaymentAttemptStatus.FAILED || status == PaymentAttemptStatus.REJECTED)
                && attempt.getFailureCode() != null && BULK_SAFE_FAILURES.contains(attempt.getFailureCode());
    }
}
