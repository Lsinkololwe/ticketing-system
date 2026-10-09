package com.pml.booking.security;

import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.pml.booking.web.graphql.mutation.RefundRequestMutationResolver;
import com.pml.booking.web.graphql.mutation.TicketMutationResolver;
import com.pml.booking.web.graphql.mutation.TicketResendMutationResolver;
import com.pml.booking.web.graphql.mutation.TicketTransferMutationResolver;
import com.pml.booking.web.graphql.query.AdminOpsResolver;
import com.pml.booking.web.graphql.query.BookingQueryResolver;
import com.pml.booking.web.graphql.query.OrganizerOpsResolver;
import com.pml.booking.web.graphql.query.RefundRequestQueryResolver;
import com.pml.booking.web.graphql.query.TicketTransferQueryResolver;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gate on each operation the booking gaps work added, written out, so loosening one (for example
 * letting an organizer call an admin-only recovery) is a failing test and not a quiet edit.
 *
 * <p>Row-level rules (the caller's own ticket, their own organization's event) are enforced in the services
 * and are proven against a database in the integration tests; this pins the coarse role gate in front of them.
 */
@Tag("L1")
@Tag("ET-ADM-003")
@DisplayName("Every new booking operation carries the role gate it is documented with")
class NewOperationsAuthorizationTest {

    private static final String AUTHENTICATED = "isAuthenticated()";
    private static final String STAFF = "hasAnyRole('ADMIN', 'FINANCE')";
    private static final String ORGANIZER_OR_STAFF = "hasAnyRole('ORGANIZER', 'ADMIN', 'FINANCE', 'SUPER_ADMIN')";
    private static final String ORGANIZER_ADMIN = "hasAnyRole('ORGANIZER', 'ADMIN', 'SUPER_ADMIN')";

    private static final Map<Class<?>, Map<String, String>> EXPECTED = Map.of(
            RefundRequestMutationResolver.class, Map.of(
                    "createAdminRefundRequest", STAFF,
                    "cancelRefundRequest", AUTHENTICATED,
                    "createUserRefundRequest", AUTHENTICATED,
                    "processRefundRequest", "hasRole('FINANCE')"),
            RefundRequestQueryResolver.class, Map.of("refundRequestsByOrganizer", ORGANIZER_OR_STAFF),
            TicketMutationResolver.class, Map.of("refundTicket", AUTHENTICATED),
            TicketResendMutationResolver.class, Map.of("resendTicket", AUTHENTICATED),
            TicketTransferMutationResolver.class, Map.of(
                    "initiateTicketTransfer", AUTHENTICATED, "cancelTicketTransfer", AUTHENTICATED,
                    "acceptTicketTransfer", AUTHENTICATED, "declineTicketTransfer", AUTHENTICATED),
            TicketTransferQueryResolver.class, Map.of(
                    "transferRecipient", AUTHENTICATED, "myTicketTransfers", AUTHENTICATED, "ticketTransferChain", AUTHENTICATED),
            BookingQueryResolver.class, Map.of(
                    "booking", AUTHENTICATED, "bookingByNumber", AUTHENTICATED, "bookingsByBuyer", AUTHENTICATED,
                    "myBookings", AUTHENTICATED, "bookingsByOrganizer", ORGANIZER_OR_STAFF),
            OrganizerOpsResolver.class, Map.of(
                    "salesOverTime", ORGANIZER_OR_STAFF, "purchasesByDayAndHour", ORGANIZER_OR_STAFF,
                    "messageTicketHolders", ORGANIZER_ADMIN, "ticketHolderAudience", ORGANIZER_ADMIN,
                    "ticketHolderMessages", ORGANIZER_ADMIN),
            AdminOpsResolver.class, Map.ofEntries(
                    Map.entry("commissionRecords", STAFF), Map.entry("gatewaySettlements", STAFF),
                    Map.entry("paymentAttemptSearch", STAFF), Map.entry("stuckTransactions", STAFF),
                    Map.entry("paymentRiskSummary", STAFF), Map.entry("resumePaymentAttempt", STAFF),
                    Map.entry("retryPaymentAttempts", STAFF), Map.entry("forceCompletePaymentAttempts", "hasRole('SUPER_ADMIN')"),
                    Map.entry("dualControlQueue", STAFF), Map.entry("confirmRecoveryAction", STAFF),
                    Map.entry("transferBetweenPlatformAccounts", STAFF), Map.entry("updateChargebackRecovery", STAFF)));

    private static Method method(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("each operation has exactly the documented @PreAuthorize")
    void gatesAreAsDocumented() {
        EXPECTED.forEach((type, operations) -> operations.forEach((name, gate) -> {
            PreAuthorize annotation = method(type, name).getAnnotation(PreAuthorize.class);
            assertThat(annotation).as("%s.%s", type.getSimpleName(), name).isNotNull();
            assertThat(annotation.value()).as("%s.%s", type.getSimpleName(), name).isEqualTo(gate);
        }));
    }

    @Test
    @DisplayName("no operation in these resolvers is left without any gate")
    void nothingIsOpen() {
        List<Class<?>> resolvers = List.copyOf(EXPECTED.keySet());
        Stream<Method> operations = resolvers.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(m -> m.isAnnotationPresent(DgsQuery.class) || m.isAnnotationPresent(DgsMutation.class));
        assertThat(operations).allSatisfy(m -> assertThat(m.isAnnotationPresent(PreAuthorize.class))
                .as("%s.%s", m.getDeclaringClass().getSimpleName(), m.getName()).isTrue());
    }

    @Test
    @DisplayName("the money, recovery, transfer and holder-messaging mutations fail closed when revocation cannot be checked")
    void sensitiveResolversFailClosed() {
        for (Class<?> type : List.of(AdminOpsResolver.class, OrganizerOpsResolver.class, TicketTransferMutationResolver.class,
                TicketResendMutationResolver.class, RefundRequestMutationResolver.class, TicketMutationResolver.class)) {
            assertThat(type.isAnnotationPresent(FailClosedOnRevocation.class)).as(type.getSimpleName()).isTrue();
        }
    }
}
