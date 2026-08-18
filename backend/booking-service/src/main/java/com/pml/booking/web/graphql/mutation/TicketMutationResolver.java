package com.pml.booking.web.graphql.mutation;

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
@RequiredArgsConstructor
public class TicketMutationResolver {

    private final TicketService ticketService;

    // validateTicket lives in CheckInMutationResolver now.
    //
    // The version that stood here was a read-modify-write on the ticket: load
    // it, see PURCHASED, set VALIDATED, save. Two stewards scanning the same
    // ticket a moment apart both read PURCHASED before either wrote, so both
    // were told the ticket was good and the ticket admitted twice. It also had
    // no event id, so a valid ticket for a different show passed at this gate.
    // See ET-TKT-003 and booking_checkins.

    // useTicket is gone with the USED state.
    //
    // It marked a VALIDATED ticket as USED, which described a two-phase gate the
    // platform never had: nothing called it during a scan, and CheckInServiceImpl
    // treated VALIDATED and USED identically as "already admitted". ET-TKT-002 R7
    // declares seven states and USED is not one of them, so the mutation had no
    // reachable target. Admission is CheckInMutationResolver's scan; there is no
    // second phase.

    /**
     * Refund a ticket.
     * processedBy is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<RefundTicketMutationResponse> refundTicket(
            @InputArgument String ticketNumber,
            @InputArgument String reason
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(processedBy -> log.info("Refunding ticket: {} by: {} reason: {}", ticketNumber, processedBy, reason))
                .flatMap(processedBy -> ticketService.refundTicket(ticketNumber, reason, processedBy)
                        .map(ticket -> new RefundTicketMutationResponse(true, "Ticket refunded successfully", ticket, List.of(), null)))
                .onErrorResume(e -> {
                    log.error("Refund ticket failed: {}", e.getMessage());
                    return Mono.just(new RefundTicketMutationResponse(false, e.getMessage(), null, List.of(e.getMessage()), null));
                });
    }

    /**
     * Cancel a ticket.
     * processedBy is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<CancelTicketMutationResponse> cancelTicket(
            @InputArgument String ticketNumber,
            @InputArgument String reason
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(processedBy -> log.info("Cancelling ticket: {} by: {} reason: {}", ticketNumber, processedBy, reason))
                .flatMap(processedBy -> ticketService.cancelTicket(ticketNumber, reason, processedBy)
                        .map(ticket -> new CancelTicketMutationResponse(true, "Ticket cancelled successfully", ticket, List.of(), null)))
                .onErrorResume(e -> {
                    log.error("Cancel ticket failed: {}", e.getMessage());
                    return Mono.just(new CancelTicketMutationResponse(false, e.getMessage(), null, List.of(e.getMessage()), null));
                });
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
    public Mono<TicketMutationResponse> adminUpdateTicket(
            @InputArgument String ticketId,
            @InputArgument AdminTicketUpdateInput input
    ) {
        log.info("Admin updating ticket: {}", ticketId);
        return ticketService.adminUpdateTicket(ticketId, input)
                .map(ticket -> TicketMutationResponse.success("Ticket updated successfully", ticket))
                .onErrorResume(e -> {
                    log.error("Admin update ticket failed: {}", e.getMessage());
                    return Mono.just(TicketMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Regenerate QR code for a ticket.
     * Schema: regenerateTicketQrCode(ticketId: ID!): TicketMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<TicketMutationResponse> regenerateTicketQrCode(@InputArgument String ticketId) {
        log.info("Regenerating QR code for ticket: {}", ticketId);
        return ticketService.regenerateTicketQrCode(ticketId)
                .map(ticket -> TicketMutationResponse.success("QR code regenerated successfully", ticket))
                .onErrorResume(e -> {
                    log.error("Regenerate QR code failed: {}", e.getMessage());
                    return Mono.just(TicketMutationResponse.error(e.getMessage()));
                });
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
