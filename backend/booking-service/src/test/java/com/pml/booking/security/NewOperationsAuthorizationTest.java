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
import com.pml.shared.security.RequiresPermission;
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

    /** What guards each operation: a platform permission, an organization permission, or plain sign-in. */
    private static final Map<Class<?>, Map<String, String>> EXPECTED = Map.of(
            RefundRequestMutationResolver.class, Map.of(
                    "createAdminRefundRequest", "permission:REFUND_MANAGE",
                    "cancelRefundRequest", AUTHENTICATED,
                    "createUserRefundRequest", AUTHENTICATED,
                    "processRefundRequest", "permission:REFUND_APPROVE"),
            RefundRequestQueryResolver.class, Map.of("refundRequestsByOrganizer", "tenant:TICKET_REFUND"),
            TicketMutationResolver.class, Map.of("refundTicket", AUTHENTICATED, "cancelTicket", "tenant:TICKET_REFUND"),
            TicketResendMutationResolver.class, Map.of("resendTicket", AUTHENTICATED),
            TicketTransferMutationResolver.class, Map.of(
                    "initiateTicketTransfer", AUTHENTICATED, "cancelTicketTransfer", AUTHENTICATED,
                    "acceptTicketTransfer", AUTHENTICATED, "declineTicketTransfer", AUTHENTICATED),
            TicketTransferQueryResolver.class, Map.of(
                    "transferRecipient", AUTHENTICATED, "myTicketTransfers", AUTHENTICATED, "ticketTransferChain", AUTHENTICATED),
            BookingQueryResolver.class, Map.of(
                    "booking", AUTHENTICATED, "bookingByNumber", AUTHENTICATED, "bookingsByBuyer", AUTHENTICATED,
                    "myBookings", AUTHENTICATED, "bookingsByOrganizer", "tenant:ATTENDEE_VIEW"),
            OrganizerOpsResolver.class, Map.of(
                    "salesOverTime", "tenant:ANALYTICS_VIEW", "purchasesByDayAndHour", "tenant:ANALYTICS_VIEW",
                    "messageTicketHolders", "tenant:EVENT_EDIT", "ticketHolderAudience", "tenant:ATTENDEE_VIEW",
                    "ticketHolderMessages", "tenant:ATTENDEE_VIEW"),
            AdminOpsResolver.class, Map.ofEntries(
                    Map.entry("commissionRecords", "permission:COMMISSION_VIEW"),
                    Map.entry("gatewaySettlements", "permission:SETTLEMENT_VIEW"),
                    Map.entry("paymentAttemptSearch", "permission:PAYMENT_VIEW"),
                    Map.entry("stuckTransactions", "permission:PAYMENT_VIEW"),
                    Map.entry("paymentRiskSummary", "permission:PAYMENT_VIEW"),
                    Map.entry("resumePaymentAttempt", "permission:TRANSACTION_RECOVER"),
                    Map.entry("retryPaymentAttempts", "permission:TRANSACTION_RECOVER"),
                    Map.entry("forceCompletePaymentAttempts", "permission:PAYMENT_APPROVE"),
                    Map.entry("dualControlQueue", "permission:TRANSACTION_RECOVER"),
                    Map.entry("confirmRecoveryAction", "permission:TRANSACTION_RECOVER"),
                    Map.entry("transferBetweenPlatformAccounts", "permission:PLATFORM_ACCOUNT_TRANSFER"),
                    Map.entry("updateChargebackRecovery", "permission:CHARGEBACK_RECOVER")));

    private static Method method(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("each operation has exactly the documented @PreAuthorize")
    void gatesAreAsDocumented() {
        EXPECTED.forEach((type, operations) -> operations.forEach((name, gate) -> {
            Method operation = method(type, name);
            RequiresPermission permission = operation.getAnnotation(RequiresPermission.class);
            com.pml.shared.security.RequiresTenantPermission tenant =
                    operation.getAnnotation(com.pml.shared.security.RequiresTenantPermission.class);
            PreAuthorize annotation = operation.getAnnotation(PreAuthorize.class);
            String actual = permission != null ? "permission:" + permission.value().name()
                    : tenant != null ? "tenant:" + tenant.value().name()
                    : annotation == null ? null : annotation.value();
            assertThat(actual).as("%s.%s", type.getSimpleName(), name).isNotNull().isEqualTo(gate);
        }));
    }

    @Test
    @DisplayName("no operation in these resolvers is left without any gate")
    void nothingIsOpen() {
        List<Class<?>> resolvers = List.copyOf(EXPECTED.keySet());
        Stream<Method> operations = resolvers.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(m -> m.isAnnotationPresent(DgsQuery.class) || m.isAnnotationPresent(DgsMutation.class));
        assertThat(operations).allSatisfy(m -> assertThat(m.isAnnotationPresent(PreAuthorize.class)
                        || m.isAnnotationPresent(RequiresPermission.class)
                        || m.isAnnotationPresent(com.pml.shared.security.RequiresTenantPermission.class))
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
