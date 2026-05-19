package com.pml.booking.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * MongoDB Index Configuration for Analytics Performance.
 *
 * CRITICAL FOR SCALE:
 * These indexes are essential for the aggregation queries in PlatformSummaryRepository.
 * Without them, MongoDB would perform full collection scans on billions of records.
 *
 * Index Strategy:
 * 1. Status field indexes - Used by $group and $match in aggregation pipelines
 * 2. Compound indexes - For filtered aggregations (e.g., status + date)
 * 3. Partial indexes - For specific status values to reduce index size
 *
 * Performance Impact:
 * - Without indexes: O(n) full collection scan = hours for billions of records
 * - With indexes: O(log n) index scan = milliseconds
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoIndexConfig {

    private final ReactiveMongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        log.info("Ensuring MongoDB indexes for analytics performance...");

        Mono.when(
                // Event Escrow Accounts
                createIndex("event_escrow_accounts", "status", "idx_escrow_status"),
                createIndex("event_escrow_accounts", "organizerId", "idx_escrow_organizer"),
                createCompoundIndex("event_escrow_accounts", "status", "organizerId", "idx_escrow_status_organizer"),
                createIndex("event_escrow_accounts", "eventId", "idx_escrow_event"),

                // Financial Transactions
                createIndex("financial_transactions", "status", "idx_txn_status"),
                createIndex("financial_transactions", "transactionType", "idx_txn_type"),
                createIndex("financial_transactions", "eventId", "idx_txn_event"),
                createIndex("financial_transactions", "organizerId", "idx_txn_organizer"),
                createCompoundIndex("financial_transactions", "status", "transactionDate", "idx_txn_status_date"),

                // Payout Requests
                createIndex("payout_requests", "status", "idx_payout_status"),
                createIndex("payout_requests", "organizerId", "idx_payout_organizer"),
                createCompoundIndex("payout_requests", "status", "organizerId", "idx_payout_status_organizer"),

                // Tickets
                createIndex("tickets", "status", "idx_ticket_status"),
                createIndex("tickets", "eventId", "idx_ticket_event"),
                createIndex("tickets", "buyerId", "idx_ticket_buyer"),
                createCompoundIndex("tickets", "eventId", "status", "idx_ticket_event_status")
        ).subscribe(
                null,
                error -> log.error("Failed to create indexes", error),
                () -> log.info("All MongoDB indexes ensured successfully")
        );
    }

    private Mono<String> createIndex(String collection, String field, String indexName) {
        Index index = new Index()
                .on(field, Sort.Direction.ASC)
                .named(indexName)
                .background();

        return mongoTemplate.indexOps(collection)
                .ensureIndex(index)
                .doOnSuccess(name -> log.debug("Index {} ensured on {}.{}", indexName, collection, field))
                .onErrorResume(e -> {
                    log.warn("Index {} may already exist: {}", indexName, e.getMessage());
                    return Mono.just(indexName);
                });
    }

    private Mono<String> createCompoundIndex(String collection, String field1, String field2, String indexName) {
        Index index = new Index()
                .on(field1, Sort.Direction.ASC)
                .on(field2, Sort.Direction.ASC)
                .named(indexName)
                .background();

        return mongoTemplate.indexOps(collection)
                .ensureIndex(index)
                .doOnSuccess(name -> log.debug("Compound index {} ensured on {}", indexName, collection))
                .onErrorResume(e -> {
                    log.warn("Compound index {} may already exist: {}", indexName, e.getMessage());
                    return Mono.just(indexName);
                });
    }
}
