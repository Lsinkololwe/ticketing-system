package com.pml.booking.service;

import com.pml.booking.domain.PaymentRiskRules;
import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.enums.PaymentAttemptType;
import com.pml.booking.domain.model.PaymentAttempt;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Scores mobile-money collections for review and summarises the platform's exposure.
 *
 * <p>An assessment is stored on the attempt so lists can filter and sort on it, and is re-worked out
 * when the attempt has changed since (a payment whose amount later fails verification must not keep the
 * clean score it was created with). Nothing here blocks or alters a payment.
 */
@Service
public class PaymentRiskService {

    /** Statuses an attempt ends in without the buyer having paid. */
    private static final Set<PaymentAttemptStatus> FAILED = Set.of(
            PaymentAttemptStatus.FAILED, PaymentAttemptStatus.REJECTED, PaymentAttemptStatus.EXPIRED);

    /** Collections are written with {@code COLLECT}; rows from before the type existed carry none. */
    private static final List<PaymentAttemptType> COLLECTIONS = java.util.Arrays.asList(PaymentAttemptType.COLLECT, null);

    /** The most attempts a summary assesses in one call, so a first call after a long gap stays bounded. */
    static final int ASSESS_BATCH = 500;

    private final ReactiveMongoTemplate template;
    private final Clock clock;

    public PaymentRiskService(ReactiveMongoTemplate template, Clock clock) {
        this.template = template;
        this.clock = clock;
    }

    public record RiskFlagCount(String flag, long count) {
    }

    public record Summary(int windowHours, long evaluated, long low, long medium, long high, long flagged,
                          BigDecimal amountAtRisk, List<RiskFlagCount> topFlags) {
    }

    /** The attempt with a current assessment; non-collections are returned as they are. */
    public Mono<PaymentAttempt> assess(PaymentAttempt attempt) {
        if (attempt.getAttemptType() != null && attempt.getAttemptType() != PaymentAttemptType.COLLECT) {
            return Mono.just(attempt);
        }
        if (attempt.getRiskEvaluatedAt() != null && attempt.getUpdatedAt() != null
                && !attempt.getUpdatedAt().isAfter(attempt.getRiskEvaluatedAt())) {
            return Mono.just(attempt);
        }
        Instant now = clock.instant();
        return facts(attempt, now).map(PaymentRiskRules::assess).flatMap(assessment -> {
            attempt.setRiskScore(assessment.score());
            attempt.setRiskFlags(assessment.flags());
            attempt.setRiskLevel(assessment.level().name());
            attempt.setRiskEvaluatedAt(now);
            // updatedAt is left alone: assessing is not a change to the payment, and moving it would
            // make every read look like a reason to assess again.
            return template.updateFirst(Query.query(Criteria.where("_id").is(attempt.getId())),
                            new Update().set("riskScore", assessment.score()).set("riskFlags", assessment.flags())
                                    .set("riskLevel", assessment.level().name()).set("riskEvaluatedAt", now),
                            PaymentAttempt.class)
                    .thenReturn(attempt);
        });
    }

    private Mono<PaymentRiskRules.Facts> facts(PaymentAttempt attempt, Instant now) {
        Mono<Long> recent = attempt.getBuyerId() == null ? Mono.just(0L) : template.count(Query.query(
                Criteria.where("buyerId").is(attempt.getBuyerId()).and("createdAt").gte(now.minus(Duration.ofMinutes(10)))
                        .and("attemptType").in(COLLECTIONS)), PaymentAttempt.class);
        Mono<Long> failures = attempt.getBuyerId() == null ? Mono.just(0L) : template.count(Query.query(
                Criteria.where("buyerId").is(attempt.getBuyerId()).and("createdAt").gte(now.minus(Duration.ofHours(1)))
                        .and("status").in(FAILED)), PaymentAttempt.class);
        Mono<Long> buyersOnPhone = attempt.getPayerPhone() == null ? Mono.just(0L) : template.findDistinct(
                        Query.query(Criteria.where("payerPhone").is(attempt.getPayerPhone())
                                .and("createdAt").gte(now.minus(Duration.ofHours(24)))),
                        "buyerId", PaymentAttempt.class, String.class)
                .count();
        return Mono.zip(recent, failures, buyersOnPhone).map(counts -> new PaymentRiskRules.Facts(
                attempt.getAmount(),
                attempt.getStatus() == PaymentAttemptStatus.CONFIRMED || attempt.getStatus() == PaymentAttemptStatus.COMPLETED,
                attempt.isAmountVerified(),
                attempt.getWebhookSignatureValid(),
                counts.getT1().intValue(),
                counts.getT2().intValue(),
                counts.getT3().intValue(),
                attempt.getRetryCount()));
    }

    /** Exposure over the last {@code windowHours}: how many collections sit at each level, and why. */
    public Mono<Summary> summary(int windowHours) {
        Instant since = clock.instant().minus(Duration.ofHours(windowHours));
        Criteria window = Criteria.where("createdAt").gte(since).and("attemptType").in(COLLECTIONS);
        return template.find(new Query(window.and("riskEvaluatedAt").is(null)).with(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .limit(ASSESS_BATCH), PaymentAttempt.class)
                .concatMap(this::assess)
                .then(Mono.defer(() -> Mono.zip(levels(since), flags(since))))
                .map(parts -> {
                    long low = 0, medium = 0, high = 0;
                    BigDecimal atRisk = BigDecimal.ZERO;
                    for (Document row : parts.getT1()) {
                        String level = row.getString("_id");
                        long count = ((Number) row.get("count")).longValue();
                        if ("HIGH".equals(level)) {
                            high = count;
                            atRisk = toDecimal(row.get("amount"));
                        } else if ("MEDIUM".equals(level)) {
                            medium = count;
                        } else if ("LOW".equals(level)) {
                            low = count;
                        }
                    }
                    return new Summary(windowHours, low + medium + high, low, medium, high, medium + high, atRisk, parts.getT2());
                });
    }

    private Mono<List<Document>> levels(Instant since) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("createdAt").gte(since).and("riskLevel").ne(null)),
                context -> new Document("$group", new Document("_id", "$riskLevel")
                        .append("count", new Document("$sum", 1))
                        .append("amount", new Document("$sum", "$amount"))));
        return template.aggregate(aggregation, PaymentAttempt.class, Document.class).collectList();
    }

    private Mono<List<RiskFlagCount>> flags(Instant since) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("createdAt").gte(since).and("riskFlags").ne(null)),
                Aggregation.unwind("riskFlags"),
                context -> new Document("$group", new Document("_id", "$riskFlags").append("count", new Document("$sum", 1))),
                Aggregation.sort(Sort.Direction.DESC, "count"),
                Aggregation.limit(5));
        return template.aggregate(aggregation, PaymentAttempt.class, Document.class)
                .map(row -> new RiskFlagCount(row.getString("_id"), ((Number) row.get("count")).longValue()))
                .collectList();
    }

    private static BigDecimal toDecimal(Object value) {
        if (value instanceof org.bson.types.Decimal128 decimal) {
            return decimal.bigDecimalValue();
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    /** Streams attempts of a page through {@link #assess} so the page shows current scores. */
    public Flux<PaymentAttempt> assessAll(List<PaymentAttempt> attempts) {
        return Flux.fromIterable(attempts).concatMap(this::assess);
    }
}
