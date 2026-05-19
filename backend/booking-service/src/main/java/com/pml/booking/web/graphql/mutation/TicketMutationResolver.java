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

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SCANNER')")
    public Mono<ValidateTicketMutationResponse> validateTicket(@InputArgument String ticketNumber) {
        log.info("Validating ticket: {}", ticketNumber);
        return ticketService.validateTicket(ticketNumber)
                .map(ticket -> new ValidateTicketMutationResponse(true, "Ticket validated successfully", ticket, List.of(), null))
                .onErrorResume(e -> {
                    log.error("Ticket validation failed: {}", e.getMessage());
                    return Mono.just(new ValidateTicketMutationResponse(false, e.getMessage(), null, List.of(e.getMessage()), null));
                });
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SCANNER')")
    public Mono<UseTicketMutationResponse> useTicket(
            @InputArgument String ticketNumber
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(scannerId -> log.info("Marking ticket as used: {} by scanner: {}", ticketNumber, scannerId))
                .flatMap(scannerId -> ticketService.useTicket(ticketNumber)
                        .map(ticket -> new UseTicketMutationResponse(true, "Ticket marked as used", ticket, List.of(), null)))
                .onErrorResume(e -> {
                    log.error("Use ticket failed: {}", e.getMessage());
                    return Mono.just(new UseTicketMutationResponse(false, e.getMessage(), null, List.of(e.getMessage()), null));
                });
    }

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
