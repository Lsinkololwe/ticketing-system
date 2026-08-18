package com.pml.booking.service.referencedata;

import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.domain.model.Ticket;
import com.pml.shared.referencedata.StatusSemanticResolver;
import com.pml.shared.referencedata.StatusSemanticStamper;
import org.reactivestreams.Publisher;
import org.springframework.data.mongodb.core.mapping.event.ReactiveBeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Binds {@link StatusSemanticStamper} to every booking model that carries a
 * workflow status.
 *
 * <p>Spring resolves entity callbacks by their generic argument, and Java forbids
 * implementing one interface twice with different type arguments, so each model
 * needs its own bean. They are one-liners on purpose — the behaviour, and the
 * reasoning for it, lives in the base class.
 */
public final class BookingStatusSemanticStampers {

    private BookingStatusSemanticStampers() {}

    @Component
    public static class Tickets extends StatusSemanticStamper<Ticket>
            implements ReactiveBeforeConvertCallback<Ticket> {

        public Tickets(StatusSemanticResolver resolver) {
            super(resolver, "TICKET_STATUS",
                    t -> t.getStatus() == null ? null : t.getStatus().name(),
                    Ticket::setStatusSemantic,
                    Ticket::getTicketNumber);
        }

        @Override
        public Publisher<Ticket> onBeforeConvert(Ticket entity, String collection) {
            return stamp(entity);
        }
    }

    @Component
    public static class Payouts extends StatusSemanticStamper<PayoutRequest>
            implements ReactiveBeforeConvertCallback<PayoutRequest> {

        public Payouts(StatusSemanticResolver resolver) {
            super(resolver, "PAYOUT_STATUS",
                    p -> p.getStatus() == null ? null : p.getStatus().name(),
                    PayoutRequest::setStatusSemantic,
                    PayoutRequest::getRequestId);
        }

        @Override
        public Publisher<PayoutRequest> onBeforeConvert(PayoutRequest entity, String collection) {
            return stamp(entity);
        }
    }

    @Component
    public static class EscrowAccounts extends StatusSemanticStamper<EventEscrowAccount>
            implements ReactiveBeforeConvertCallback<EventEscrowAccount> {

        public EscrowAccounts(StatusSemanticResolver resolver) {
            super(resolver, "ESCROW_STATUS",
                    e -> e.getStatus() == null ? null : e.getStatus().name(),
                    EventEscrowAccount::setStatusSemantic,
                    EventEscrowAccount::getEventId);
        }

        @Override
        public Publisher<EventEscrowAccount> onBeforeConvert(EventEscrowAccount entity, String collection) {
            return stamp(entity);
        }
    }
}
