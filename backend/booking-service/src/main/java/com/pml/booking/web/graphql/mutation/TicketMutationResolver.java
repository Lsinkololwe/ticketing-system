package com.pml.booking.web.graphql.mutation;

import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.web.graphql.dto.*;
import com.pml.booking.service.TicketService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import com.pml.booking.domain.model.Ticket;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TenantBoundary;
import com.pml.shared.security.Permission;

/**
 * GraphQL Mutation Resolver for Ticket Operations
 *
 * <p>Business Intent: Handles all ticket state changes including validation,
 * usage marking, refunds, transfers, and cancellations. All mutations
 * are secured with role-based permissions.</p>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: All actor IDs (processedBy, scannerId, etc.)
 *       are extracted from JWT, never from client input</li>
 * </ul>
 */
@Slf4j


@DgsComponent
@FailClosedOnRevocation
@Validated
@RequiredArgsConstructor
public class TicketMutationResolver {

    private final TicketService ticketService;
    private final com.pml.booking.repository.TicketRepository ticketRepository;
    private final com.pml.booking.security.OrganizerAccess access;
    private final com.pml.booking.workflow.refund.RefundProcess refundProcess;

    // Admission is not here: it is CheckInMutationResolver's scan. A read-modify-write on
    // the ticket (load, check, set VALIDATED, save) lets two stewards scanning the same
    // ticket a moment apart both read it as admissible before either writes, and admits it
    // twice; it also has no event id, so a valid ticket for a different show would pass.
    // The scan records a booking_checkins row, whose unique ticketId makes admission single.
    //
    // There is no second admission phase: VALIDATED is the only admitted state.

    /**
     * Refund a ticket, in full or in part, to whoever paid for it.
     *
     * <p>The caller must hold {@code ticket:refund} on the ticket's event (an organizer's team member
     * with that permission, or platform staff). They are the decision, so the refund goes straight to
     * the refund workflow approved, which debits the event's escrow, claws back the matching share of
     * the commission, and sends the money back through the provider under one id. An {@code amount}
     * is checked against what remains refundable on the seat before anything is written; leaving it
     * out refunds what remains. The ticket returned is its state now: it turns {@code REFUNDED} when the
     * provider confirms, and a part refund leaves it admissible with {@code refundedAmount} raised.
     * Anyone else is told the ticket does not exist.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Ticket> refundTicket(
            @InputArgument String ticketNumber,
            @InputArgument String reason,
            @InputArgument java.math.BigDecimal amount
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actor -> ticketRepository.findByTicketNumber(ticketNumber)
                        .switchIfEmpty(Mono.error(() -> TenantBoundary.refuse(ErrorCode.TICKET_UNKNOWN, "ticket " + ticketNumber)))
                        .flatMap(ticket -> access.requireEvent(ticket.getEventId(), Permission.TICKET_REFUND)
                                .onErrorMap(DomainRefusal.class, refusal -> TenantBoundary.refuse(ErrorCode.TICKET_UNKNOWN, "ticket " + ticketNumber))
                                .thenReturn(ticket))
                        .doOnNext(ticket -> log.info("Refunding ticket {} by {} amount {} reason {}", ticketNumber, actor, amount, reason))
                        .flatMap(ticket -> refundProcess.requestAsOperator(ticket.getId(), reason, actor, amount)
                                .then(ticketRepository.findByTicketNumber(ticketNumber))));
    }

    /**
     * Cancel a ticket.
     * processedBy is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Ticket> cancelTicket(
            @InputArgument String ticketNumber,
            @InputArgument String reason
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(processedBy -> log.info("Cancelling ticket: {} by: {} reason: {}", ticketNumber, processedBy, reason))
                .flatMap(processedBy -> ticketService.cancelTicket(ticketNumber, reason, processedBy));
    }

    // ========================================================================
    // ADMIN TICKET OPERATIONS
    // ========================================================================

    /**
     * Admin update ticket details.
     * Schema: adminUpdateTicket(ticketId: ID!, input: AdminTicketUpdateInput!): TicketMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Ticket> adminUpdateTicket(
            @InputArgument String ticketId,
            @Valid @InputArgument AdminTicketUpdateInput input
    ) {
        log.info("Admin updating ticket: {}", ticketId);
        return ticketService.adminUpdateTicket(ticketId, input);
    }

    /**
     * Regenerate QR code for a ticket.
     * Schema: regenerateTicketQrCode(ticketId: ID!): TicketMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Ticket> regenerateTicketQrCode(@InputArgument String ticketId) {
        log.info("Regenerating QR code for ticket: {}", ticketId);
        return ticketService.regenerateTicketQrCode(ticketId);
    }

    /**
     * Bulk cancel tickets.
     * Schema: bulkCancelTickets(ticketIds: [ID!]!, reason: String!): BulkOperationResponse!
     * processedBy is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<BulkOperationResponse> bulkCancelTickets(
            @InputArgument List<String> ticketIds,
            @InputArgument String reason
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(processedBy -> log.info("Bulk cancelling {} tickets by: {}", ticketIds.size(), processedBy))
                .flatMap(processedBy -> ticketService.bulkCancelTickets(ticketIds, reason, processedBy))
                .onErrorResume(e -> {
                    log.error("Bulk cancel tickets failed: {}", e.getMessage());
                    return Mono.just(BulkOperationResponse.error("Bulk cancel failed: " + e.getMessage(), List.of(e.getMessage())));
                });
    }
}
