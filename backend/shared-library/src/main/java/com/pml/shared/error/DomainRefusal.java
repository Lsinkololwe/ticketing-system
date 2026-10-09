package com.pml.shared.error;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The base type every deliberate refusal extends.
 *
 * <h2>A refusal is not a defect</h2>
 * "This tier is sold out" and "the database is unreachable" are both
 * exceptions in Java and nothing alike to a client. A refusal is the platform
 * working correctly and declining; it carries a registry code, a retryability
 * judgement, and whatever details the client needs to act. Anything that is not
 * a {@code DomainRefusal} is a defect, and defects tell the client nothing
 * beyond {@code INTERNAL_ERROR} — see {@code GraphQlErrors}.
 *
 * <h2>The message never crosses the boundary</h2>
 * {@link #getMessage()} exists for logs. What reaches the client is the code,
 * the classification, {@code retryable} and {@link #details()} — every one of
 * which the platform chose deliberately. An exception message is written for
 * whoever is debugging, routinely contains an id, a query or a class name, and
 * is exactly the kind of thing that should not be echoed to a caller who
 * triggered the error on purpose to see what falls out.
 *
 * <p>This is not a style preference, and the codebase proves it: the resolvers
 * this replaces called {@code buildError(ex, …, ex.getMessage(), …)}, so
 * {@code "Ticket not found: 7f3a… (validation scan)"} and {@code "user@example.com
 * already exists"} were being returned to whoever asked. The second is an
 * account-enumeration oracle reachable without a session. Because the message
 * stops here, a throw site is free to be as specific as the log needs —
 * specificity in the message is useful precisely because it goes nowhere.</p>
 *
 * <p>User-facing copy is the frontend's job (Track F0-6 FE-1): one mapping from
 * {@link ErrorCode} to a sentence, shared by all three apps and translatable.
 * A message composed on the server would be a second, untranslated copy of that
 * mapping, and it would be the copy nobody updates.</p>
 *
 * <h2>Details are a closed, deliberate map</h2>
 * The registry names the extension keys each code may carry — {@code retryAfterSeconds},
 * {@code suggestedSlug}, {@code missingFields}. They are values a client acts
 * on, not free-form diagnostics, which is why this takes a map rather than a
 * formatted string.
 */
public abstract class DomainRefusal extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    /**
     * @param developerMessage for the log, and only the log. Be specific; it
     *                         does not cross the boundary.
     */
    protected DomainRefusal(ErrorCode errorCode, String developerMessage) {
        this(errorCode, developerMessage, Map.of());
    }

    /**
     * @param developerMessage for the log, and only the log — see the class
     *                         note. Never a string assembled for a user.
     * @param details          the extension keys the registry permits for this code.
     *                         These <em>do</em> reach the client, so they carry
     *                         values a client acts on and nothing incidental.
     */
    protected DomainRefusal(ErrorCode errorCode, String developerMessage,
                            Map<String, Object> details) {
        // No cause, and no stack trace capture beyond what RuntimeException
        // already does: a refusal is an expected outcome on a hot path, and
        // filling in a stack trace for every sold-out tier at on-sale is real
        // cost for information nobody reads.
        super(developerMessage);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** Convenience: the classification the registry assigns this code. */
    public ErrorClassification classification() {
        return errorCode.classification();
    }

    /**
     * Whether retrying could succeed.
     *
     * <p>Taken from the registry rather than from the call site, so the same code
     * cannot be retryable in one service and not in another — which would make
     * the client's decision depend on which service answered.</p>
     */
    public boolean retryable() {
        return errorCode.retryable();
    }

    /** Extension values the client acts on. Never diagnostics. */
    public Map<String, Object> details() {
        return details;
    }

    /** Builder-ish helper for the common "one detail" case. */
    protected static Map<String, Object> detail(String key, Object value) {
        Map<String, Object> single = new LinkedHashMap<>();
        single.put(key, value);
        return single;
    }
}
