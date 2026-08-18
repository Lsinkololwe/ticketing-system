package com.pml.booking.service.impl;

import com.pml.booking.domain.PayoutEligibility;
import com.pml.shared.constants.ChargebackStatus;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.repository.ChargebackRecordRepository;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.repository.PayoutRequestRepository;
import com.pml.booking.service.PayoutEligibilityService;
import com.pml.shared.constants.PayoutRequestStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Gathers the values eligibility is decided on, then defers to
 * {@link PayoutEligibility#evaluate}.
 *
 * <p>Deliberately thin. Every rule lives in the value object so that the
 * request path and this query cannot drift apart, and so the rules can be
 * tested without a database. What this class contributes is the four reads and
 * the platform minimum.
 *
 * @see <a href="file:../../../../../../../specs/finance/003-payouts-and-settlement/spec.md">ET-FIN-003</a>
 */
@Slf4j
@Service
public class PayoutEligibilityServiceImpl implements PayoutEligibilityService {

    /**
     * Chargeback states that still threaten the money.
     *
     * <p>WON and LOST are settled: the dispute is over and the balance already
     * reflects it. Blocking on those would freeze an organizer's payouts
     * forever over a chargeback they won months ago.
     */
    private static final Set<ChargebackStatus> OPEN_DISPUTES = EnumSet.of(
            ChargebackStatus.RECEIVED,
            ChargebackStatus.UNDER_REVIEW,
            ChargebackStatus.DISPUTED);

    /** Payout states that occupy the escrow account. */
    private static final Set<PayoutRequestStatus> OPEN_REQUESTS = EnumSet.of(
            PayoutRequestStatus.PENDING,
            PayoutRequestStatus.APPROVED,
            PayoutRequestStatus.PROCESSING);

    private final EventEscrowAccountRepository escrowRepository;
    private final ChargebackRecordRepository chargebackRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final BigDecimal minimumPayout;

    public PayoutEligibilityServiceImpl(
            EventEscrowAccountRepository escrowRepository,
            ChargebackRecordRepository chargebackRepository,
            PayoutRequestRepository payoutRequestRepository,
            @Value("${payment.escrow.minimum-payout-amount:10.00}") BigDecimal minimumPayout) {
        this.escrowRepository = escrowRepository;
        this.chargebackRepository = chargebackRepository;
        this.payoutRequestRepository = payoutRequestRepository;
        this.minimumPayout = minimumPayout;
    }

    @Override
    public Mono<PayoutEligibility> evaluate(String eventId, String organizerId) {
        return escrowRepository.findByEventId(eventId)
                .flatMap(escrow -> evaluateFor(escrow, organizerId))
                .switchIfEmpty(Mono.fromSupplier(this::noEscrow));
    }

    @Override
    public Mono<PayoutEligibility> evaluateForEscrow(String escrowAccountId, String organizerId) {
        return escrowRepository.findById(escrowAccountId)
                .flatMap(escrow -> evaluateFor(escrow, organizerId))
                .switchIfEmpty(Mono.fromSupplier(this::noEscrow));
    }

    private Mono<PayoutEligibility> evaluateFor(EventEscrowAccount escrow, String organizerId) {
        // An event belonging to someone else answers the same as an event that
        // does not exist. Anything more specific — "not yours" versus "no such
        // event" — tells a stranger which event ids are real and what their
        // hold dates are.
        if (organizerId == null || !organizerId.equals(escrow.getOrganizerId())) {
            return Mono.fromSupplier(this::noEscrow);
        }

        return Mono.zip(
                chargebackRepository.countByEventIdAndStatusIn(escrow.getEventId(), OPEN_DISPUTES)
                        .defaultIfEmpty(0L),
                payoutRequestRepository
                        .findByEscrowAccountIdAndStatusIn(escrow.getId(), OPEN_REQUESTS)
                        .hasElements()
        ).map(t -> PayoutEligibility.evaluate(
                escrow.getStatus(),
                escrow.getHoldUntil(),
                escrow.getCurrentBalance(),
                t.getT1(),
                t.getT2(),
                minimumPayout,
                Instant.now()));
    }

    private PayoutEligibility noEscrow() {
        return PayoutEligibility.evaluate(
                null, null, BigDecimal.ZERO, 0L, false, minimumPayout, Instant.now());
    }
}
