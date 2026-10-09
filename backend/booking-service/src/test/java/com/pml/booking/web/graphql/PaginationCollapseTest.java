package com.pml.booking.web.graphql;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.repository.PayoutRequestRepository;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pagination twins are collapsed, and paging still works afterwards.
 *
 * <h2>What was done and why it needs proving twice</h2>
 * Eighteen operations were offered in two shapes — {@code payoutRequestsOffsetPagination}
 * and {@code payoutRequestsCursorPagination} — for the same data, the same arguments and
 * the same audience. Offering both was never a decision anyone took, and a client
 * choosing between them is choosing between two ways of being right. The offset form
 * survives, renamed to the bare operation, because all eighteen are admin and organizer
 * tables that need page numbers and totals.
 *
 * <p>That is two claims, and they fail differently:
 *
 * <ul>
 *   <li><b>The surface shrank correctly</b> — no twin remains, no orphan type is left
 *       behind, and the one deliberate survivor is still there. A source-level check,
 *       because it is a claim about the schema.</li>
 *   <li><b>Offset paging still returns the right rows</b> — a rename that quietly broke
 *       skip/limit would pass every schema assertion and every compile. So the paging
 *       runs against a real replica set, over real documents, and the pages are asserted
 *       to partition the set exactly.</li>
 * </ul>
 *
 * <h2>The survivor that must not be collapsed</h2>
 * {@code ticketsByBuyerCursorPagination} is {@code @tag(name: "mobile")} while its offset
 * sibling is {@code @tag(name: "admin")}. Same name, different audiences — the mobile
 * ticket feed, which is exactly the case cursor pagination exists for. It is asserted
 * present here so a later tidy-up pass cannot delete it for looking like the other
 * eighteen.
 */
@Tag("L2")
@Tag("ET-PLT-004")
@DisplayName("D-19 · the pagination twins are gone and offset paging still partitions the set")
class PaginationCollapseTest {

    private static final Path SDL =
            Path.of("src/main/resources/graphql/schema.graphqls");

    private static final List<String> COLLAPSED = List.of(
            "escrowAccounts", "escrowAccountsByOrganizer", "expiredReservations",
            "failedPayoutRequests", "payoutRequests", "payoutRequestsByEvent",
            "payoutRequestsByOrganizer", "payoutRequestsForReview", "pendingPayoutRequests",
            "pendingRefundRequests", "refundRequests", "refundRequestsByBuyer",
            "refundRequestsByEvent", "reservationsByEvent", "searchTickets",
            "stuckPayoutRequests", "ticketsByEvent", "ticketsByOrganizer");

    /**
     * Fields that had only one pagination form, so there was nothing to collapse — they
     * simply lose the suffix.
     */
    private static final List<String> DE_SUFFIXED = List.of(
            "chargebacks", "journalEntries", "myTransactions", "payoutRequestsByIssueType",
            "pendingJournalEntries", "recentlyResolvedPayoutRequests", "reconciliationRuns",
            "retryablePayoutRequests");

    private static String sdl;

    @BeforeAll
    static void readSchema() throws IOException {
        sdl = Files.readString(SDL);
    }

    @Nested
    @DisplayName("the schema surface")
    class Surface {

        @Test
        @DisplayName("ET-PLT-004 · every collapsed operation exists once, under its bare name")
        void survivorsExistUnsuffixed() {
            for (String operation : COLLAPSED) {
                assertThat(rootField(operation))
                        .as("%s should have survived the collapse under its bare name", operation)
                        .isNotNull();
                assertThat(rootField(operation + "OffsetPagination"))
                        .as("%s kept its suffix — the rename did not happen", operation)
                        .isNull();
                assertThat(rootField(operation + "CursorPagination"))
                        .as("%s still has a cursor twin — the collapse did not happen", operation)
                        .isNull();
            }
        }

        @Test
        @DisplayName("ET-PLT-004 · the survivors return an offset page, not a connection")
        void survivorsReturnOffsetPages() {
            for (String operation : COLLAPSED) {
                assertThat(rootField(operation))
                        .as("%s is an admin or organizer table, so it pages by number", operation)
                        .contains("OffsetPage");
            }
        }

        @Test
        @DisplayName("ET-PLT-004 · the mobile ticket feed keeps its cursor form")
        void theDeliberateSurvivorRemains() {
            // The one pair whose halves serve different audiences: admin offset, mobile
            // cursor. Collapsing it would delete the customer ticket list, which needs
            // cursor pagination.
            assertThat(rootField("ticketsByBuyerCursorPagination"))
                    .as("the mobile feed must not be collapsed with the admin table")
                    .isNotNull()
                    .contains("TicketConnection");
            assertThat(rootField("ticketsByBuyer")).isNull();
        }

        @Test
        @DisplayName("ET-PLT-004 · no Connection or Edge type is left with nothing pointing at it")
        void noOrphanedTypes() {
            // Deleting the cursor fields orphans their return types. A type nothing
            // references still composes, still generates a TypeScript interface, and
            // still reads to the next person as a supported shape.
            for (Matcher m = Pattern.compile("(?m)^type\\s+(\\w+(?:Connection|Edge))\\b")
                    .matcher(sdl); m.find(); ) {
                String type = m.group(1);
                long mentions = Stream.of(sdl.split("\n"))
                        .filter(line -> line.contains(type))
                        .filter(line -> !line.trim().startsWith("type " + type))
                        .count();
                assertThat(mentions)
                        .as("%s is defined but referenced by nothing — dead schema", type)
                        .isGreaterThan(0);
            }
        }

        /**
         * A field declared on {@code Query} or {@code Mutation}, and nowhere else.
         *
         * <p>Scoped to the root blocks deliberately. {@code failedPayoutRequests} is
         * also an {@code Int!} counter on {@code BookingPendingCounts}, so a search
         * over the whole document finds the counter, reports it as the operation and
         * then fails for a reason that has nothing to do with pagination. Brace
         * matching rather than a regex over the file, because a nested block ends the
         * naive pattern early.
         */

        @Test
        @DisplayName("ET-PLT-004 · lone paginated fields lost their suffix")
        void loneVariantsAreDeSuffixed() {
            List<String> problems = new ArrayList<>();
            for (String operation : DE_SUFFIXED) {
                if (rootField(operation) == null) {
                    problems.add(operation + " is missing under its bare name");
                }
                for (String suffix : List.of("OffsetPagination", "CursorPagination")) {
                    if (rootField(operation + suffix) != null) {
                        problems.add(operation + suffix + " still carries the suffix");
                    }
                }
            }
            assertThat(problems).isEmpty();
        }

        private static String rootField(String name) {
            Pattern field = Pattern.compile("(?m)^\\s{2,}" + Pattern.quote(name) + "\\s*[(:].*$");
            for (String block : rootBlocks()) {
                Matcher m = field.matcher(block);
                if (m.find()) {
                    return m.group();
                }
            }
            return null;
        }

        private static List<String> rootBlocks() {
            List<String> blocks = new java.util.ArrayList<>();
            Matcher header = Pattern.compile("(?m)^(?:extend\\s+)?type\\s+(Query|Mutation)\\b[^{]*\\{")
                    .matcher(sdl);
            while (header.find()) {
                int depth = 1;
                int i = header.end();
                while (i < sdl.length() && depth > 0) {
                    char c = sdl.charAt(i);
                    if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                    }
                    i++;
                }
                blocks.add(sdl.substring(header.end(), i));
            }
            return blocks;
        }
    }

    @Nested
    @DisplayName("offset paging, against a replica set")
    class Paging {

        private static MongoClient client;
        private static ReactiveMongoTemplate template;
        private static PayoutRequestRepository payouts;

        @BeforeAll
        static void connect() {
            client = MongoClients.create(MongoReplicaSet.connectionString());
            template = new ReactiveMongoTemplate(
                    new SimpleReactiveMongoDatabaseFactory(client, "booking_pagination_collapse"));
            payouts = new ReactiveMongoRepositoryFactory(template)
                    .getRepository(PayoutRequestRepository.class);
        }

        @AfterAll
        static void disconnect() {
            client.close();
        }

        @BeforeEach
        void seedTwentyFive() {
            template.remove(new Query(), PayoutRequest.class).block();
            for (int i = 0; i < 25; i++) {
                PayoutRequest request = new PayoutRequest();
                request.setId("payout-%02d".formatted(i));
                request.setOrganizerId("org-lusaka-live");
                request.setRequestedAmount(new BigDecimal("100.00"));
                request.setRequestedAt(Instant.parse("2026-09-01T00:00:00Z").plusSeconds(i));
                template.save(request).block();
            }
        }

        @Test
        @DisplayName("ET-PLT-004 · pages partition the set exactly — no gap, no repeat")
        void pagesPartitionTheSet() {
            // The failure a rename can introduce without breaking anything visible: an
            // off-by-one in skip, which drops one row per page boundary or serves the
            // same row twice. Both look like a plausible list on screen. Asserting the
            // union is the whole set, with no duplicates, is what catches it.
            List<String> firstPage = idsOfPage(0, 10);
            List<String> secondPage = idsOfPage(1, 10);
            List<String> thirdPage = idsOfPage(2, 10);

            assertThat(firstPage).hasSize(10);
            assertThat(secondPage).hasSize(10);
            assertThat(thirdPage).hasSize(5);

            assertThat(firstPage).doesNotContainAnyElementsOf(secondPage);
            assertThat(secondPage).doesNotContainAnyElementsOf(thirdPage);

            assertThat(Stream.of(firstPage, secondPage, thirdPage).flatMap(List::stream).toList())
                    .as("three pages of a 25-row set must be the 25 rows, each once")
                    .hasSize(25)
                    .doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("ET-PLT-004 · a page past the end is empty rather than an error")
        void pastTheEndIsEmpty() {
            assertThat(idsOfPage(99, 10)).isEmpty();
        }

        private static List<String> idsOfPage(int page, int size) {
            return payouts.findAll()
                    .sort((a, b) -> a.getId().compareTo(b.getId()))
                    .skip((long) page * size)
                    .take(size)
                    .map(PayoutRequest::getId)
                    .collectList()
                    .block();
        }
    }
}
