package com.pml.shared.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Converts money fields already stored as strings into {@link org.bson.types.Decimal128}.
 *
 * <h2>Why this must ship with the converters, not after them</h2>
 * {@link MoneyConversions} only changes how NEW documents are written. Every
 * document already in the database holds a string, and an aggregation that now
 * expects a number skips them without complaint — so between deploying the
 * converter and running this, a revenue total silently reports only the sales
 * made since the deploy. That is a worse failure than the one being fixed,
 * because it looks plausible.
 *
 * <h2>Idempotent by construction</h2>
 * The filter is {@code $type: "string"}, so a field already converted is not
 * matched. Re-running is a no-op, and a run interrupted halfway can simply be
 * run again.
 *
 * <h2>Unconvertible values are left alone, loudly</h2>
 * {@code $toDecimal} raises rather than coerces on a value that is not a number,
 * so a corrupt row fails its own update instead of silently becoming zero. The
 * count of failures is reported; a zero would be indistinguishable from a
 * genuinely empty balance.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class MoneyFieldMigrationService {

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    /**
     * Every collection and the money fields it carries.
     *
     * <p>Listed explicitly rather than discovered by reflection: a field is
     * money because of what it means, not because it is a {@code BigDecimal}.
     * A conversion rate or a percentage is also a BigDecimal and converting it
     * here would be wrong.
     */
    private static final Map<String, List<String>> MONEY_FIELDS = Map.ofEntries(
            Map.entry("tickets", List.of(
                    "price", "originalPrice", "discountAmount", "commissionAmount", "netAmount")),
            Map.entry("booking_payout_requests", List.of(
                    "requestedAmount", "netPayoutAmount", "processingFee", "settledAmount")),
            Map.entry("booking_escrow_accounts", List.of(
                    "currentBalance", "totalDeposits", "totalWithdrawals", "totalRefunds",
                    "pendingWithdrawals", "totalCommissions")),
            Map.entry("escrow_accounts", List.of(
                    "currentBalance", "totalDeposits", "totalWithdrawals", "totalCommissions",
                    "minimumBalance", "maximumPayoutAmount")),
            Map.entry("escrow_transactions", List.of("amount", "balanceAfter")),
            Map.entry("journal_lines", List.of("amount")),
            Map.entry("platform_accounts", List.of("currentBalance")),
            Map.entry("commission_records", List.of(
                    "grossAmount", "commissionAmount", "netAmount")),
            Map.entry("payment_intents", List.of("amount", "capturedAmount", "refundedAmount")),
            Map.entry("payment_attempts", List.of("amount")),
            Map.entry("refund_requests", List.of("requestedAmount", "approvedAmount", "refundFee")),
            Map.entry("chargebacks", List.of("amount", "feeAmount")),
            Map.entry("bank_accounts", List.of("microDepositAmount")),
            Map.entry("booking_reservations", List.of("totalAmount")),
            Map.entry("promo_codes", List.of("discountAmount", "minimumPurchaseAmount")));

    /** @param converted fields updated, @param failed fields whose values would not coerce */
    public record Result(long converted, long failed, List<String> failures) {}

    public Mono<Result> migrate() {
        return Flux.fromIterable(MONEY_FIELDS.entrySet())
                .concatMap(entry -> Flux.fromIterable(entry.getValue())
                        .concatMap(field -> convertField(entry.getKey(), field)))
                .reduce(new Result(0, 0, List.of()), (acc, one) -> new Result(
                        acc.converted() + one.converted(),
                        acc.failed() + one.failed(),
                        concat(acc.failures(), one.failures())))
                .doOnSuccess(r -> log.info(
                        "Money field migration: {} fields converted to Decimal128, {} failed {}",
                        r.converted(), r.failed(), r.failures()));
    }

    private static List<String> concat(List<String> a, List<String> b) {
        if (b.isEmpty()) {
            return a;
        }
        return Stream.concat(a.stream(), b.stream()).toList();
    }

    private Mono<Result> convertField(String collection, String field) {
        Document filter = new Document(field, new Document("$type", "string"));
        // An aggregation-pipeline update, so the new value is derived from the
        // old one inside the server. Reading every document into the JVM to
        // rewrite it would be slower and would race with live traffic.
        List<Document> pipeline = List.of(
                new Document("$set", new Document(field, new Document("$toDecimal", "$" + field))));

        return mongoTemplateProvider.getObject().getMongoDatabase()
                .flatMap(db -> Mono.from(db.getCollection(collection).updateMany(filter, pipeline)))
                .map(result -> {
                    long n = result.getModifiedCount();
                    if (n > 0) {
                        log.info("  {}.{}: {} documents converted", collection, field, n);
                    }
                    return new Result(n, 0, List.<String>of());
                })
                .onErrorResume(error -> {
                    // A value that will not coerce raises rather than becoming
                    // zero. Report it — a wrong balance is invisible, a reported
                    // failure is not.
                    String where = collection + "." + field + ": " + error.getMessage();
                    log.warn("  could not convert {}", where);
                    return Mono.just(new Result(0, 1, List.of(where)));
                });
    }
}
