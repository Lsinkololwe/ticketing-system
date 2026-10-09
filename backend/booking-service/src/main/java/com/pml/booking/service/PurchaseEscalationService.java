package com.pml.booking.service;

import com.pml.booking.domain.model.PurchaseEscalation;
import com.pml.booking.repository.PurchaseEscalationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Hands a purchase to a human, once.
 *
 * <h2>Why raising twice must be harmless</h2>
 * The purchase workflow escalates when a payment stays pending past its limit, and
 * an activity retry or a duplicate provider callback can raise the same problem
 * again. If each
 * attempt created a row, one stuck purchase would become a hundred queue entries
 * overnight and the queue would stop being read — which is the same outcome as
 * having no escalation at all, arrived at more slowly.
 *
 * <p>The unique index on {@code reservationId} is what enforces that, and the
 * duplicate-key it throws is swallowed here deliberately: "already escalated" is
 * success, not failure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseEscalationService {

    private final PurchaseEscalationRepository escalations;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;

    /**
     * Records that a purchase needs an operator, unless it already has been.
     *
     * @return the escalation, whether this call created it or found it
     */
    public Mono<PurchaseEscalation> raise(String reservationId,
                                          String eventId,
                                          String userId,
                                          String paymentIntentId,
                                          PurchaseEscalation.Reason reason,
                                          String detail,
                                          BigDecimal amount,
                                          String currency) {
        return escalations.findByReservationId(reservationId)
                .doOnNext(existing -> log.debug(
                        "Reservation {} is already escalated ({}) — not raising a duplicate",
                        reservationId, existing.getReason()))
                .switchIfEmpty(Mono.defer(() -> escalations.save(PurchaseEscalation.builder()
                                .reservationId(reservationId)
                                .eventId(eventId)
                                .userId(userId)
                                .paymentIntentId(paymentIntentId)
                                .reason(reason)
                                .detail(detail)
                                .amount(amount)
                                .currency(currency)
                                .build())
                        .doOnSuccess(saved -> log.error(
                                "ESCALATED to ET-ADM-003: reservation {} — {} ({}). "
                                        + "This needs an operator; nothing else will resolve it.",
                                reservationId, reason, detail))
                        // Two concurrent raises can reach the switchIfEmpty at
                        // once. The index refuses the second write, and losing
                        // that race means the escalation exists, which is what
                        // the caller wanted.
                        .onErrorResume(DuplicateKeyException.class,
                                e -> escalations.findByReservationId(reservationId))));
    }

    /** Marks an escalation dealt with, so it leaves the operator's queue. */
    public Mono<PurchaseEscalation> resolve(String reservationId, String resolvedBy, String resolution) {
        return escalations.findByReservationId(reservationId)
                .flatMap(escalation -> {
                    escalation.setResolved(true);
                    escalation.setResolvedBy(resolvedBy);
                    escalation.setResolvedAt(clock.instant());
                    escalation.setResolution(resolution);
                    return escalations.save(escalation);
                });
    }
}
