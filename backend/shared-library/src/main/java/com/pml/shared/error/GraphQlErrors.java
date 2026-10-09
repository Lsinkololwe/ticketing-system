package com.pml.shared.error;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the {@code extensions} payload every refusal reaches the client with.
 *
 * <h2>This is the boundary, and it is a security boundary</h2>
 * Everything a caller learns about a failure is decided here. That makes it the
 * one place where an information leak is cheap to prevent and, once past,
 * impossible to recall.
 *
 * <p>The rule is inverted from the obvious one: nothing is included unless it
 * was deliberately chosen. {@link #forDefect} takes a {@code Throwable} and
 * <b>ignores it entirely</b> — no message, no class name, no frame, no cause
 * chain. A {@code NullPointerException} from a resolver and a
 * {@code MongoTimeoutException} from the driver produce byte-identical output,
 * because the difference between them is information about the platform's
 * internals that a caller has no legitimate use for and an attacker does.</p>
 *
 * <h2>What replaces the message</h2>
 * A correlation id. The response carries {@code correlationId}, the log
 * carries the same id and the full detail. A user quotes the id to support and
 * support finds everything — without the platform having narrated its stack
 * trace to whoever asked.
 *
 * <h2>Timing is part of the response</h2>
 * The rule that a cross-tenant id be indistinguishable from a
 * non-existent one covers the body <em>and the timing shape</em>. This class
 * cannot enforce timing, but callers must not add a lookup on one path and not
 * the other — see {@code TenantBoundaryTest}.
 */
public final class GraphQlErrors {

    /** Extension keys, named once so a typo cannot silently create a new one. */
    public static final String ERROR_CODE = "errorCode";
    public static final String CLASSIFICATION = "classification";
    public static final String RETRYABLE = "retryable";
    public static final String CORRELATION_ID = "correlationId";

    private GraphQlErrors() {
    }

    /**
     * The extensions for a deliberate refusal.
     *
     * @param correlationId ties this response to the log line holding the detail
     */
    public static Map<String, Object> forRefusal(DomainRefusal refusal, String correlationId) {
        Map<String, Object> extensions = base(
                refusal.errorCode(), refusal.retryable(), correlationId);

        // Details are the values the registry says this code may carry. They are added
        // last and cannot overwrite the contract keys above — a detail called
        // "retryable" must not be able to flip the client's retry decision.
        refusal.details().forEach((key, value) -> {
            if (!extensions.containsKey(key)) {
                extensions.put(key, value);
            }
        });
        return extensions;
    }

    /**
     * The extensions for anything that is not a deliberate refusal.
     *
     * <p>The {@code Throwable} parameter exists so call sites read naturally and
     * so a future change cannot quietly start including it. It is not read. Log
     * the throwable against {@code correlationId} instead — that is the whole
     * exchange this class makes.</p>
     */
    public static Map<String, Object> forDefect(Throwable ignored, String correlationId) {
        return base(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.retryable(), correlationId);
    }

    /**
     * The message a client is shown for a defect.
     *
     * <p>Constant, and deliberately so: a message that varies with the cause is
     * a side channel that survives every attempt to strip the cause itself.</p>
     */
    public static String defectMessage() {
        return "The request could not be completed.";
    }

    /**
     * The message a client is shown for a refusal: the code, and nothing else.
     *
     * <p>Not {@code refusal.getMessage()}. That string is written for a log and
     * is routinely assembled from data — {@code "Ticket not found: 7f3a…"},
     * {@code "user@example.com already exists"} — which is how the resolvers
     * this replaces turned every refusal into an information disclosure, the
     * second one into an account-enumeration oracle.</p>
     *
     * <p>Returning the code name adds nothing the client was not already given
     * in {@code extensions.errorCode}, which is the point: it is readable in a
     * raw response and provably free of anything the throw site knew. Copy for
     * users is built from the code on the frontend (FE-1), where it can be
     * translated.</p>
     */
    public static String refusalMessage(ErrorCode code) {
        return code.name();
    }

    private static Map<String, Object> base(ErrorCode code, boolean retryable, String correlationId) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put(ERROR_CODE, code.name());
        extensions.put(CLASSIFICATION, code.classification().name());
        extensions.put(RETRYABLE, retryable);
        if (correlationId != null && !correlationId.isBlank()) {
            extensions.put(CORRELATION_ID, correlationId);
        }
        return extensions;
    }
}
