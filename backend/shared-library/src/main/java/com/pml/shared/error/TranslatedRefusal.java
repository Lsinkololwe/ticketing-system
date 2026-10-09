package com.pml.shared.error;

import java.util.Map;

/**
 * A {@link DomainRefusal} produced by a {@link RefusalTranslator} rather than
 * thrown directly by domain code.
 *
 * <p>{@code DomainRefusal} is abstract so that a service raising a refusal has
 * to name it — {@code TierSoldOut}, {@code ReservationExpired} — which reads at
 * the throw site and groups in a stack trace. A translator has no such name to
 * give: it is converting somebody else's exception, and the meaning is carried
 * entirely by the {@link ErrorCode}. This is the concrete carrier for that case
 * and deliberately nothing more.</p>
 */
public final class TranslatedRefusal extends DomainRefusal {

    public TranslatedRefusal(ErrorCode errorCode, String developerMessage) {
        super(errorCode, developerMessage);
    }

    public TranslatedRefusal(ErrorCode errorCode, String developerMessage,
                             Map<String, Object> details) {
        super(errorCode, developerMessage, details);
    }
}
