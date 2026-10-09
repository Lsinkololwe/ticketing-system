package com.pml.booking.reservation;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every reservation path that takes a caller-supplied id checks who is asking.
 * OWASP A01:2021 · CWE-639.
 *
 * <h2>Why a lint beside the runtime test</h2>
 * {@code ReservationVisibilityTest} proves the rule holds. It cannot prove the resolver still
 * applies it: the rule is a filter one line long, and deleting it leaves a method that compiles,
 * reads naturally, and returns the reservation to whoever asked. There is no failing assertion
 * anywhere unless something specifically looks for the filter, which is what this does.
 *
 * <p>Three operations take a reservation id from the client. All three must scope, and they do it
 * three different ways because they answer to three different rules — which is exactly why a
 * count of guards would not be enough and each is named here.
 */
@Tag("L4")
@Tag("ET-TKT-001")
@DisplayName("ET-TKT-001 · no reservation is reachable on its id alone")
class ReservationScopeLintTest {

    private static final Path RESOLVERS = Path.of("src/main/java/com/pml/booking/web/graphql");
    private static final Path QUERIES = RESOLVERS.resolve("query/ReservationQueryResolver.java");
    private static final Path MUTATIONS = RESOLVERS.resolve("mutation/ReservationMutationResolver.java");
    private static final Path SDL = Path.of("src/main/resources/graphql/schema.graphqls");
    private static final Path PURCHASE = Path.of("src/main/java/com/pml/booking/workflow/purchase/PurchaseProcess.java");

    /**
     * Comments only, so prose describing a filter cannot stand in for the filter.
     *
     * <p>String literals are deliberately kept: the role names this test checks
     * ({@code "ROLE_ADMIN"} and the rest) <em>are</em> string literals, and stripping them would
     * leave the assertions comparing against nothing.
     */
    private static final Pattern COMMENTS = Pattern.compile(
            "/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    private static String queries;
    private static String mutations;
    private static String purchase;

    @BeforeAll
    static void read() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(QUERIES), "booking sources not present");
        queries = COMMENTS.matcher(Files.readString(QUERIES)).replaceAll(match -> "");
        mutations = COMMENTS.matcher(Files.readString(MUTATIONS)).replaceAll(match -> "");
        purchase = COMMENTS.matcher(Files.readString(PURCHASE)).replaceAll(match -> "");
    }

    @Test
    @DisplayName("ET-TKT-001 · the by-id read compares the reservation's buyer against the caller")
    void theByIdReadIsScoped() {
        Matcher body = Pattern.compile(
                        "Mono<TicketReservation> reservation\\(@InputArgument String id\\)\\s*\\{(.*?)\\n    \\}",
                        Pattern.DOTALL)
                .matcher(queries);
        assertThat(body.find())
                .as("reservation(id) has moved or been renamed — re-point this lint")
                .isTrue();

        assertThat(body.group(1))
                .as("""
                    Returning findById(id) unfiltered publishes the buyer's identity, their tier \
                    choices, their promo code and the exact money to any signed-in caller holding \
                    an id — an account that costs a phone number to obtain.""")
                .contains("visibleTo");
    }

    @Test
    @DisplayName("ET-TKT-001 · support is an explicit set of roles, not any elevated role")
    void supportIsAClosedSet() {
        Matcher declaration = Pattern.compile(
                        "SUPPORT_AUTHORITIES\\s*=\\s*Set\\.of\\(([^)]*)\\)", Pattern.DOTALL)
                .matcher(queries);
        assertThat(declaration.find())
                .as("SUPPORT_AUTHORITIES has moved or changed shape — re-point this lint")
                .isTrue();

        // Scoped to the declaration, not the file: ROLE_ORGANIZER appears legitimately on
        // reservationsByEvent, which checks the event before answering.
        assertThat(declaration.group(1))
                .as("""
                    An organizer reads the reservations for their own event through \
                    reservationsByEvent, which checks the event. This path knows nothing about \
                    events, so admitting the role here would open every buyer's reservation to \
                    every organizer on the platform.""")
                .contains("ROLE_ADMIN")
                .contains("ROLE_FINANCE")
                .doesNotContain("ROLE_ORGANIZER")
                .doesNotContain("ROLE_CUSTOMER");
    }

    @Test
    @DisplayName("ET-TKT-001 · cancellation still matches the caller against the hold's buyer")
    void cancellationIsScoped() {
        Matcher body = Pattern.compile(
                        "Mono<Boolean> cancelReservation\\(@InputArgument String reservationId\\)\\s*\\{(.*?)\\n    \\}",
                        Pattern.DOTALL)
                .matcher(mutations);
        assertThat(body.find()).as("cancelReservation has moved — re-point this lint").isTrue();

        assertThat(body.group(1))
                .as("the caller comes from the JWT and is passed as a buyer, never as an operator")
                .contains("requireCurrentUserId")
                .contains("purchaseProcess.cancel(userId, reservationId, false)");

        Matcher process = Pattern.compile(
                        "Mono<Boolean> cancel\\(String actorId, String reservationId, boolean administrative\\)\\s*\\{(.*?)\\n    \\}",
                        Pattern.DOTALL)
                .matcher(purchase);
        assertThat(process.find()).as("PurchaseProcess.cancel has moved — re-point this lint").isTrue();
        assertThat(process.group(1))
                .as("""
                    Without the comparison a buyer releases somebody else's hold seconds before an \
                    on-sale and the seats land back in the pool for them to take.""")
                .contains("administrative || actorId.equals(reservation.getUserId())");
    }

    @Test
    @DisplayName("ET-TKT-001 · confirmPurchase is not client-callable")
    void confirmPurchaseIsNotInTheSchema() throws IOException {
        // A gate box in its own right. Confirmation moves inventory to sold, writes tickets,
        // credits escrow and records commission; it is driven by the payment callback, and a
        // client-callable form would let a buyer confirm a reservation they have not paid for.
        assertThat(Files.readString(SDL))
                .as("confirmation is reached from the payment result, never from the client")
                .doesNotContain("confirmPurchase");
    }
}
