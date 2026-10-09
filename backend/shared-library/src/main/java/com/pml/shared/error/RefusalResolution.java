package com.pml.shared.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Decides whether a throwable is a refusal, for every transport.
 *
 * <h2>Why this is shared rather than written twice</h2>
 * The GraphQL handler and the REST advice must reach the same verdict about the
 * same exception — that is what "one vocabulary, two transports" means. Two
 * copies of this logic agree on the day they are written and diverge on the day
 * one of them is fixed: a new wrapper type unwrapped here and not there, and
 * suddenly the REST surface reports {@code INTERNAL_ERROR} for refusals the
 * GraphQL surface classifies correctly. Nothing fails; the two just stop
 * matching, and the parity test would be comparing one implementation against a
 * copy of itself.
 *
 * <p>Sharing it means parity is structural. There is one place to be wrong, and
 * both transports are wrong together — which is far easier to notice than one
 * of them being quietly right.</p>
 */
public final class RefusalResolution {

    private static final Logger log = LoggerFactory.getLogger(RefusalResolution.class);

    private RefusalResolution() {
    }

    /**
     * The refusal this exception represents, if any.
     *
     * <p>A translator that throws is treated as not recognising the exception.
     * Losing the original failure to a bug in the code meant to describe it is
     * the worst available outcome: the real error is replaced by an error about
     * error handling, and the stack trace points at this file.</p>
     */
    public static Optional<DomainRefusal> resolve(Throwable exception,
                                                  List<RefusalTranslator> translators) {
        Throwable unwrapped = unwrap(exception);
        if (unwrapped instanceof DomainRefusal refusal) {
            return Optional.of(refusal);
        }
        if (translators == null) {
            return Optional.empty();
        }
        for (RefusalTranslator translator : translators) {
            try {
                Optional<DomainRefusal> translated = translator.translate(unwrapped);
                if (translated != null && translated.isPresent()) {
                    return translated;
                }
            } catch (RuntimeException translatorFailed) {
                log.warn("refusal translator {} failed; treating as unrecognised",
                        translator.getClass().getName(), translatorFailed);
            }
        }
        return Optional.empty();
    }

    /**
     * Digs the real failure out of the wrappers a reactive chain adds.
     *
     * <p>Every refusal on this platform is raised inside a {@code Mono}, so it
     * arrives wrapped in a {@code CompletionException} or similar. Checking
     * {@code instanceof DomainRefusal} on the outer throwable classifies all of
     * them as defects — safe, and useless: every sold-out tier would report
     * {@code INTERNAL_ERROR}. That disables the entire contract while every
     * leak test still passes.</p>
     *
     * <p>Stops at the first {@code DomainRefusal} rather than at the root, since
     * a refusal may itself have been caused by something more specific that the
     * platform has already chosen not to describe.</p>
     */
    public static Throwable unwrap(Throwable exception) {
        Throwable current = exception;
        while (current != null
                && !(current instanceof DomainRefusal)
                && current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }
        return current == null ? exception : current;
    }
}
