package com.pml.shared.error;

/**
 * The sanctioned way to refuse a reach across a tenant boundary.
 *
 * <h2>The refusal must be indistinguishable from "no such thing"</h2>
 * Answering {@code ACTOR_NOT_PERMITTED} when an actor asks for another tenant's
 * record is truthful and is an information leak: it confirms the id exists.
 * Anyone holding a list of candidate ids learns which are real by reading the
 * error code, and needs no access to anything to do it. That is an enumeration
 * oracle, and it is why this is a security requirement rather than a matter of
 * taste.
 *
 * <p>So a cross-tenant reach returns exactly what a genuinely unknown id
 * returns: {@code EVENT_UNKNOWN}, {@code ORGANIZATION_UNKNOWN},
 * {@code RESERVATION_UNKNOWN}. Same code, same message, same empty details.
 * The caller cannot tell the two apart, because from where they stand there is
 * no difference worth telling.</p>
 *
 * <h2>Only {@code *_UNKNOWN} codes are accepted, and that is enforced</h2>
 * The failure this class exists to prevent is a call site passing
 * {@code ACTOR_NOT_PERMITTED} — which reads as the more precise, more helpful
 * answer and reopens the oracle in one line. {@link #refuse} rejects any code
 * whose name does not end in {@code _UNKNOWN}, so the mistake fails loudly at
 * the point it is made rather than quietly on the wire.
 *
 * <h2>What this does not fix</h2>
 * This shapes the answer once a tenant check has decided to refuse. It does not
 * perform the check, and it cannot: nothing here knows which tenant the caller
 * belongs to. An endpoint that never compares the resource's owner against the
 * caller does not reach this class at all — it returns the record.
 */
public final class TenantBoundary {

    private static final String UNKNOWN_SUFFIX = "_UNKNOWN";

    private TenantBoundary() {
    }

    /**
     * Refuses without disclosing that the resource exists.
     *
     * @param unknownCode the code this resource returns for an id that was
     *                    never issued — {@code EVENT_UNKNOWN} and so on. Must
     *                    be the same code the not-found path uses, or the two
     *                    answers differ and the oracle survives in a subtler
     *                    form.
     * @param what        for the log only. Say precisely what was reached for
     *                    and by whom; this is the record that a boundary was
     *                    tested, and it goes nowhere near the response.
     */
    public static DomainRefusal refuse(ErrorCode unknownCode, String what) {
        if (unknownCode == null || !unknownCode.name().endsWith(UNKNOWN_SUFFIX)) {
            throw new IllegalArgumentException(
                    "a cross-tenant refusal must answer with the same *_UNKNOWN code an "
                            + "unissued id would produce, so the two are indistinguishable; "
                            + unknownCode + " tells the caller the resource exists");
        }
        // No details map. A detail naming the resource, the owning tenant or the
        // permission required would restore exactly what withholding the code
        // was meant to prevent.
        return new TranslatedRefusal(unknownCode, what);
    }
}
