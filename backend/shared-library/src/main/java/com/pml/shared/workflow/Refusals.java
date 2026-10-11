package com.pml.shared.workflow;

import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.RefusalTranslator;
import com.pml.shared.error.TranslatedRefusal;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.failure.ApplicationFailure;
import reactor.core.Exceptions;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A refusal keeps its {@link ErrorCode} through a workflow and back.
 *
 * <p>Inside Temporal a refusal is a non-retryable {@link ApplicationFailure} whose type is the
 * {@code ErrorCode} name. An activity raises one from a {@link DomainRefusal}, or from a service's own exception
 * through that service's {@link RefusalTranslator}, so there is one mapping; an update handler
 * re-raises it so the caller sees the refusal rather than an activity failure; and the client side
 * turns it back into a {@link TranslatedRefusal}, so GraphQL reports the same code the service
 * layer chose.
 */
public final class Refusals {

    private static final Pattern CODED = Pattern.compile("^([A-Z][A-Z_]+): (.*)$", Pattern.DOTALL);

    private Refusals() {
    }

    /**
     * A refusal as Temporal records it: non-retryable, typed by its code, and with the code leading
     * the message. The message carries it too because a refusal raised by an update validator
     * reaches the client with its type replaced by the SDK's own wrapper class and only its message
     * intact.
     */
    public static ApplicationFailure refusal(ErrorCode code, String message) {
        return ApplicationFailure.newNonRetryableFailure(code.name() + ": " + message, code.name());
    }

    /** What an activity rethrows for an error its reactive chain raised. */
    public static RuntimeException forActivity(Throwable error) {
        return forActivity(error, unmapped -> Optional.empty());
    }

    /**
     * As {@link #forActivity(Throwable)}, first mapping the service's own exceptions to their codes
     * through its translator.
     */
    public static RuntimeException forActivity(Throwable error, RefusalTranslator translator) {
        Throwable root = Exceptions.unwrap(error);
        if (root instanceof ApplicationFailure failure) {
            return failure;
        }
        if (root instanceof DomainRefusal refusal) {
            return refusal.retryable()
                    ? ApplicationFailure.newFailure(refusal.getMessage(), refusal.errorCode().name())
                    : refusal(refusal.errorCode(), refusal.getMessage());
        }
        Optional<DomainRefusal> translated = translator.translate(root);
        if (translated.isPresent()) {
            // The exception's own message names the record and its state, which is what an
            // operator reading the workflow history needs; the code is what the caller receives.
            return refusal(translated.get().errorCode(), String.valueOf(root.getMessage()));
        }
        if (isDocumentValidationFailure(error)) {
            // The collection's validator refused the document itself: the same document is refused again,
            // so retrying only delays the answer while the caller times out. Every other database error is
            // left to retry: a write conflict is transient, and a create-or-get that lost a race on a
            // duplicate key succeeds on its next attempt.
            return ApplicationFailure.newNonRetryableFailure(String.valueOf(root.getMessage()), root.getClass().getName());
        }
        if ((root instanceof IllegalStateException || root instanceof IllegalArgumentException)
                && !String.valueOf(root.getMessage()).startsWith("Timeout on blocking read")) {
            // A refused business rule, not a transient fault: retrying it only repeats the refusal.
            return ApplicationFailure.newNonRetryableFailure(root.getMessage(), root.getClass().getName());
        }
        return root instanceof RuntimeException runtime ? runtime : new IllegalStateException(root);
    }

    /** MongoDB error 121: the document does not satisfy the collection's {@code $jsonSchema}. */
    private static final int DOCUMENT_VALIDATION_FAILURE = 121;

    static boolean isDocumentValidationFailure(Throwable error) {
        for (Throwable cause = error; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof com.mongodb.MongoException mongo && mongo.getCode() == DOCUMENT_VALIDATION_FAILURE) {
                return true;
            }
            if (cause instanceof com.mongodb.MongoBulkWriteException bulk && bulk.getWriteErrors().stream()
                    .anyMatch(failure -> failure.getCode() == DOCUMENT_VALIDATION_FAILURE)) {
                return true;
            }
        }
        return false;
    }

    /** Re-raises a failed activity inside an update handler as the refusal it carries. */
    public static RuntimeException rethrow(RuntimeException failure) {
        return applicationCause(failure)
                .<RuntimeException>map(cause -> ApplicationFailure.newNonRetryableFailure(
                        cause.getOriginalMessage(), cause.getType()))
                .orElse(failure);
    }

    public static String typeOf(Throwable failure, String fallback) {
        return applicationCause(failure)
                .map(cause -> {
                    ErrorCode code = codeOf(cause);
                    return code != null ? code.name() : cause.getType();
                })
                .orElse(fallback);
    }

    public static String messageOf(Throwable failure) {
        return applicationCause(failure).map(Refusals::plainMessage).orElse(failure.getMessage());
    }

    /**
     * The innermost application failure in the chain. A refusal from an update validator reaches the
     * client wrapped in an outer application failure typed by the SDK's own exception class, so the
     * outermost one names the transport and the innermost one names the refusal.
     */
    public static Optional<ApplicationFailure> applicationCause(Throwable failure) {
        ApplicationFailure innermost = null;
        for (Throwable cause = failure; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof ApplicationFailure application) {
                innermost = application;
            }
        }
        return Optional.ofNullable(innermost);
    }

    /**
     * Client side · back to the platform's refusal.
     *
     * @param whenNotOpen the code for "an execution is already open" or "none is", which the
     *                    caller's domain names — a payout and an organization refuse differently
     */
    public static Throwable fromTemporal(Throwable error, ErrorCode whenNotOpen) {
        if (error instanceof DomainRefusal) {
            return error;
        }
        for (Throwable cause = error; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof ApplicationFailure application) {
                ErrorCode code = codeOf(application);
                if (code != null) {
                    return new TranslatedRefusal(code, plainMessage(application));
                }
                if (IllegalStateException.class.getName().equals(application.getType())) {
                    return new IllegalStateException(plainMessage(application));
                }
                if (IllegalArgumentException.class.getName().equals(application.getType())) {
                    return new IllegalArgumentException(plainMessage(application));
                }
            }
            if (cause instanceof WorkflowExecutionAlreadyStarted) {
                return new TranslatedRefusal(whenNotOpen, "a process is already open for this record");
            }
            if (cause instanceof WorkflowNotFoundException) {
                return new TranslatedRefusal(whenNotOpen, "no open process for this record");
            }
        }
        return error;
    }

    /** The refusal code a failure carries, by its type or, when a wrapper replaced the type, by its message. */
    static ErrorCode codeOf(ApplicationFailure failure) {
        ErrorCode byType = codeNamed(failure.getType());
        if (byType != null) {
            return byType;
        }
        Matcher coded = CODED.matcher(String.valueOf(failure.getOriginalMessage()));
        return coded.matches() ? codeNamed(coded.group(1)) : null;
    }

    private static String plainMessage(ApplicationFailure failure) {
        String message = String.valueOf(failure.getOriginalMessage());
        Matcher coded = CODED.matcher(message);
        return coded.matches() && codeNamed(coded.group(1)) != null ? coded.group(2) : message;
    }

    private static ErrorCode codeNamed(String type) {
        if (type == null) {
            return null;
        }
        try {
            return ErrorCode.valueOf(type);
        } catch (IllegalArgumentException notACode) {
            return null;
        }
    }
}
