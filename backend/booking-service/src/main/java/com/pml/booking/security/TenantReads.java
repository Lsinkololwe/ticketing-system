package com.pml.booking.security;

import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.domain.model.PromoCode;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.repository.BankAccountRepository;
import com.pml.booking.repository.PayoutRequestRepository;
import com.pml.booking.repository.PromoCodeRepository;
import com.pml.booking.repository.TicketRepository;
import com.pml.shared.dto.EventSummaryDto;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TenantBoundary;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantGuard;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Loads organization-owned booking records for the caller of the current request.
 *
 * <p>Every lookup puts the caller's organizations into the query, so a record owned by another
 * organization is never read. A refused record is answered with the same {@code *_UNKNOWN} code an
 * id that was never issued produces, so the answer never reveals that the record exists. Callers the
 * tenant scope marks platform-wide (see {@link TenantScopeConfiguration}) read any record.
 */
@Component
public class TenantReads {

    private final TicketRepository tickets;
    private final PromoCodeRepository promoCodes;
    private final BankAccountRepository bankAccounts;
    private final PayoutRequestRepository payoutRequests;
    private final CatalogServiceClient catalog;

    public TenantReads(TicketRepository tickets, PromoCodeRepository promoCodes, BankAccountRepository bankAccounts,
                       PayoutRequestRepository payoutRequests, CatalogServiceClient catalog) {
        this.tickets = tickets;
        this.promoCodes = promoCodes;
        this.bankAccounts = bankAccounts;
        this.payoutRequests = payoutRequests;
        this.catalog = catalog;
    }

    /** A ticket the caller bought, or one belonging to one of the caller's organizations. */
    public Mono<Ticket> ticketForCaller(String ticketId) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(caller -> tickets.findByIdAndBuyerId(ticketId, caller))
                .switchIfEmpty(Mono.defer(() -> CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                        scope,
                        tickets.findById(ticketId),
                        organizationIds -> tickets.findByIdAndOrganizationIdIn(ticketId, organizationIds),
                        ErrorCode.TICKET_UNKNOWN,
                        "ticket " + ticketId))));
    }

    /** As {@link #ticketForCaller}, found by the number printed on the ticket. */
    public Mono<Ticket> ticketByNumberForCaller(String ticketNumber) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(caller -> tickets.findByTicketNumberAndBuyerId(ticketNumber, caller))
                .switchIfEmpty(Mono.defer(() -> CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                        scope,
                        tickets.findByTicketNumber(ticketNumber),
                        organizationIds -> tickets.findByTicketNumberAndOrganizationIdIn(ticketNumber, organizationIds),
                        ErrorCode.TICKET_UNKNOWN,
                        "ticket number " + ticketNumber))));
    }

    public Mono<PromoCode> promoCodeForCaller(String promoCodeId) {
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                promoCodes.findById(promoCodeId),
                organizationIds -> promoCodes.findByIdAndOrganizationIdIn(promoCodeId, organizationIds),
                ErrorCode.PROMO_CODE_UNKNOWN,
                "promo code " + promoCodeId));
    }

    public Mono<PromoCode> promoCodeByCodeForCaller(String code) {
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                promoCodes.findByCodeIgnoreCase(code),
                organizationIds -> promoCodes.findByCodeIgnoreCaseAndOrganizationIdIn(code, organizationIds),
                ErrorCode.PROMO_CODE_UNKNOWN,
                "promo code " + code));
    }

    public Mono<BankAccount> bankAccountForCaller(String bankAccountId) {
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                bankAccounts.findById(bankAccountId),
                organizationIds -> bankAccounts.findByIdAndOrganizationIdIn(bankAccountId, organizationIds),
                ErrorCode.BANK_ACCOUNT_UNKNOWN,
                "bank account " + bankAccountId));
    }

    public Mono<PayoutRequest> payoutRequestForCaller(String payoutRequestId) {
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                payoutRequests.findById(payoutRequestId),
                organizationIds -> payoutRequests.findByIdAndOrganizationIdIn(payoutRequestId, organizationIds),
                ErrorCode.PAYOUT_REQUEST_UNKNOWN,
                "payout request " + payoutRequestId));
    }

    public Mono<PayoutRequest> payoutRequestByRequestIdForCaller(String requestId) {
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                payoutRequests.findByRequestId(requestId),
                organizationIds -> payoutRequests.findByRequestIdAndOrganizationIdIn(requestId, organizationIds),
                ErrorCode.PAYOUT_REQUEST_UNKNOWN,
                "payout request " + requestId));
    }

    /**
     * The organization that owns {@code eventId}, when it is one of the caller's. Catalog is the
     * authority on which organization an event belongs to; an event the caller does not own is
     * refused exactly as an event that does not exist.
     */
    public Mono<String> eventOrganizationForCaller(String eventId) {
        return catalog.getEventById(eventId)
                .mapNotNull(EventSummaryDto::getOrganizationId)
                .flatMap(organizationId -> CurrentTenantScope.get()
                        .filter(scope -> scope.permits(organizationId))
                        .map(scope -> organizationId))
                .switchIfEmpty(Mono.error(() -> TenantBoundary.refuse(ErrorCode.EVENT_UNKNOWN, "event " + eventId)));
    }
}
