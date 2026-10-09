package com.pml.shared.error;

import java.util.Optional;

/**
 * Turns an exception this platform did not author into a {@link DomainRefusal}.
 *
 * <h2>Why a translator rather than making every exception a refusal</h2>
 * Two kinds of exception reach the error boundary and only one of them can be
 * edited. {@code OptimisticLockingFailureException} comes from Spring,
 * {@code AccessDeniedException} from Spring Security, {@code MongoWriteException}
 * from the driver — all of them describe outcomes the client genuinely needs to
 * distinguish, and none of them can be made to extend {@code DomainRefusal}.
 *
 * <p>The services' own exceptions could have been converted in place, and were
 * not, for a reason worth stating: their messages are assembled from data.
 * {@code "Ticket not found: %s (%s)"}, {@code "%s with %s '%s' already exists"} —
 * these were written for a log file, and the old resolver copied them to the
 * client verbatim. Converting the class without rewriting the message would
 * carry that leak forward under a new name, and it would look like progress.
 * A translator forces every message to be written fresh, at one reviewable
 * site per service, with the client as the audience.</p>
 *
 * <h2>Order matters and is explicit</h2>
 * Translators are consulted in {@code @Order} sequence and the first non-empty
 * answer wins. Service translators run before {@link PlatformRefusalTranslator}
 * so a service can be more specific about an exception the platform also knows
 * — for example distinguishing a duplicate idempotency key from any other
 * duplicate key, which the platform cannot infer alone.
 */
@FunctionalInterface
public interface RefusalTranslator {

    /**
     * @return the refusal this exception represents, or empty if this
     *         translator does not recognise it — <em>not</em> a guess. Returning
     *         a refusal for an unrecognised exception is how a defect starts
     *         being reported to clients as a business outcome, which suppresses
     *         the alert and tells the user their input was at fault.
     */
    Optional<DomainRefusal> translate(Throwable exception);
}
