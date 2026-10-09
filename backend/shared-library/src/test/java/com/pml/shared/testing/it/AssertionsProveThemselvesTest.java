package com.pml.shared.testing.it;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.testing.Harness;
import com.pml.shared.testing.Inventory;
import com.pml.shared.testing.Ledger;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.Persistence;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every assertion in the harness, watched failing.
 *
 * <p>A deliberately write-then-throw service must fail the assertion, and that is the point.
 * An assertion nobody has seen fail is an assumption. These helpers are used across the whole
 * corpus, so if
 * one of them silently passes on everything, the corpus verifies green on a broken platform —
 * which is worse than having no assertion at all, because it looks like proof.
 *
 * <p>So each case here seeds a specific defect and requires the assertion to catch it, then
 * seeds the correct state and requires it to pass.
 */
@Tag("L2")
@Tag("ET-PLT-006")
@DisplayName("ET-PLT-006-R4 · the harness assertions catch what they claim to catch")
class AssertionsProveThemselvesTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "harness_assertions"));
    }

    @AfterAll
    static void disconnect() {
        Harness.unbind();
        client.close();
    }

    @BeforeEach
    void bindAndClear() {
        Harness.bind(template);
        drop("booking_reservations", "booking_tier_inventory", "booking_journal_lines");
    }

    // ------------------------------------------------------------ Persistence

    @Nested
    @DisplayName("Persistence.assertNothingPersisted")
    class PersistenceAssertion {

        @Test
        @DisplayName("catches a service that writes and then throws")
        void catchesWriteThenThrow() {
            // The defect: the refusal was raised after the write, so the caller
            // was told no and the database was told yes.
            writeThenThrow();

            assertThatThrownBy(() -> Persistence.assertNothingPersisted("booking_reservations"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("booking_reservations")
                    .hasMessageContaining("refused");
        }

        @Test
        @DisplayName("passes when the refusal genuinely wrote nothing")
        void passesOnACleanRefusal() {
            assertThatCode(() -> Persistence.assertNothingPersisted("booking_reservations"))
                    .doesNotThrowAnyException();
        }

        private void writeThenThrow() {
            try {
                template.save(new Document("_id", "res-1"), "booking_reservations")
                        .then(reactor.core.publisher.Mono.error(new IllegalStateException("refused, too late")))
                        .block();
            } catch (IllegalStateException expected) {
                // the service refused — and left its write behind
            }
        }
    }

    // ------------------------------------------------------------- Inventory

    @Nested
    @DisplayName("Inventory.assertConserved")
    class InventoryAssertion {

        @Test
        @DisplayName("catches a leaked hold — the counters no longer sum to capacity")
        void catchesDrift() {
            // 100 capacity, but only 99 accounted for: a reserve decremented
            // available and never incremented reserved.
            seedTier("tier-leak", 100, 89, 10, 0);

            assertThatThrownBy(() -> Inventory.assertConserved("tier-leak"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("not conserved")
                    .hasMessageContaining("drift -1");
        }

        @Test
        @DisplayName("catches an oversell — a counter went negative")
        void catchesNegativeCounter() {
            seedTier("tier-oversell", 100, -1, 101, 0);

            assertThatThrownBy(() -> Inventory.assertConserved("tier-oversell"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("negative counter");
        }

        @Test
        @DisplayName("passes across a legitimate reserve → confirm → release cycle")
        void passesOnConservedMovements() {
            seedTier("tier-ok", 100, 100, 0, 0);
            assertThatCode(() -> Inventory.assertConserved("tier-ok")).doesNotThrowAnyException();

            seedTier("tier-ok", 100, 90, 10, 0);   // reserved 10
            assertThatCode(() -> Inventory.assertConserved("tier-ok")).doesNotThrowAnyException();

            seedTier("tier-ok", 100, 90, 0, 10);   // confirmed those 10
            assertThatCode(() -> Inventory.assertConserved("tier-ok")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("refuses to silently pass over a tier that does not exist")
        void refusesUnknownTier() {
            assertThatThrownBy(() -> Inventory.assertConserved("tier-absent"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("no booking_tier_inventory row");
        }
    }

    // ---------------------------------------------------------------- Ledger

    @Nested
    @DisplayName("Ledger.assertBalanced")
    class LedgerAssertion {

        @Test
        @DisplayName("catches an unbalanced entry")
        void catchesUnbalanced() {
            line("e1", "2010", "CREDIT", "100.00");
            line("e1", "1010", "DEBIT", "90.00");

            assertThatThrownBy(Ledger::assertBalanced)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("unbalanced")
                    .hasMessageContaining("difference");
        }

        @Test
        @DisplayName("catches a single-line entry — one line is not double entry")
        void catchesSingleLine() {
            line("e2", "2010", "CREDIT", "100.00");

            assertThatThrownBy(Ledger::assertBalanced)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("at least 2");
        }

        @Test
        @DisplayName("catches a negative amount — direction carries the sign")
        void catchesNegativeAmount() {
            line("e3", "2010", "CREDIT", "100.00");
            line("e3", "1010", "DEBIT", "-100.00");

            assertThatThrownBy(Ledger::assertBalanced)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("non-positive");
        }

        @Test
        @DisplayName("catches money stored as a double rather than Decimal128")
        void catchesFloatingPointMoney() {
            line("e4", "2010", "CREDIT", "100.00");
            template.save(new Document("journalEntryId", "e4")
                    .append("accountCode", "1010")
                    .append("direction", "DEBIT")
                    .append("amount", 100.00d), "booking_journal_lines").block();

            assertThatThrownBy(Ledger::assertBalanced)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Decimal128");
        }

        @Test
        @DisplayName("passes on a balanced pair, and on scale differences of the same amount")
        void passesOnBalanced() {
            line("e5", "2010", "CREDIT", "100.00");
            line("e5", "1010", "DEBIT", "100.0");   // same amount, different scale

            assertThatCode(Ledger::assertBalanced).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an empty ledger is trivially balanced")
        void passesOnEmptyLedger() {
            assertThatCode(Ledger::assertBalanced).doesNotThrowAnyException();
        }
    }

    // --------------------------------------------------------------- helpers

    private void drop(String... collections) {
        for (String collection : collections) {
            template.dropCollection(collection).block();
        }
    }

    private void seedTier(String tierId, int capacity, int available, int reserved, int sold) {
        template.save(new Document("_id", tierId)
                .append("capacity", capacity)
                .append("availableQuantity", available)
                .append("reservedQuantity", reserved)
                .append("soldQuantity", sold), "booking_tier_inventory").block();
    }

    private void line(String entryId, String accountCode, String direction, String amount) {
        template.save(new Document("journalEntryId", entryId)
                .append("accountCode", accountCode)
                .append("direction", direction)
                .append("amount", new Decimal128(new BigDecimal(amount)))
                .append("currency", "ZMW"), "booking_journal_lines").block();
    }

    @Test
    @DisplayName("ET-PLT-006-R4 · the short form requires a bound template rather than failing obscurely")
    void shortFormRequiresBinding() {
        Harness.unbind();
        assertThatThrownBy(() -> Persistence.assertNothingPersisted("booking_reservations"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Harness.bind");
        assertThat(true).isTrue();
    }
}
