package com.pml.shared.error;

import com.pml.shared.security.revocation.RevocationUnavailableException;
import com.pml.shared.security.revocation.TokenRevokedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import java.util.Optional;

/**
 * Registry codes for the failures no service authored.
 *
 * <p>Ordered last, so a service translator that knows more about the same
 * exception answers first — a duplicate key on the idempotency index is
 * {@code IDEMPOTENCY_KEY_REUSED} and must never be retried, while a duplicate
 * key anywhere else is a retryable {@code RESOURCE_CONFLICT}. Only the service
 * knows which index it was.</p>
 *
 * <h2>{@code IllegalArgumentException} and {@code IllegalStateException} are absent on purpose</h2>
 * Both are tempting to map to {@code BAD_REQUEST}, and both must stay defects.
 * On this platform they are raised by invariants a developer asserted —
 * {@code Objects.requireNonNull}, an unreachable switch branch, a misconfigured
 * bean — so they mean the server has a bug, not that the caller sent something
 * wrong.
 *
 * <p>Classifying a server bug as a client error costs twice. The caller is told
 * to fix input that is already correct, so they retry, edit, and give up; and
 * the log records a handled business refusal, so no alert fires and nobody
 * learns the bug exists. Left untranslated they land in the defect path —
 * {@code INTERNAL_ERROR} to the caller, stack trace at ERROR in the log.</p>
 *
 * <p>A genuine input problem that currently surfaces as one of these is fixed
 * by raising a refusal at that site with the code the case deserves. A blanket
 * rule here would be right occasionally and wrong silently.</p>
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class PlatformRefusalTranslator implements RefusalTranslator {

    @Override
    public Optional<DomainRefusal> translate(Throwable exception) {
        // Validation first, and it is the only branch that can match several
        // exception types at once, so it is expressed as a lookup rather than a
        // switch arm.
        Optional<DomainRefusal> validation = ValidationTranslation.of(exception);
        if (validation.isPresent()) {
            return validation;
        }

        return Optional.ofNullable(switch (exception) {

            // Authentication and authorisation.
            case TokenRevokedException ignored -> new TranslatedRefusal(
                    ErrorCode.TOKEN_REVOKED,
                    "token presented after revocation");

            case RevocationUnavailableException ignored -> new TranslatedRefusal(
                    // Retryable per the registry: the check failed closed
                    // because the revocation store was unreachable, not because
                    // the token was bad. Telling the client it is permanent
                    // would sign out every session during a Redis blip.
                    ErrorCode.REVOCATION_UNAVAILABLE,
                    "revocation store unreachable; failing closed");

            case AuthenticationException ignored -> new TranslatedRefusal(
                    ErrorCode.ACTOR_NOT_AUTHENTICATED,
                    "no usable authentication on the request");

            // `SecurityContextUtils.requireCurrentUserId()` raises this when the
            // request carries no identity, which is the same outcome Spring
            // Security's own exception describes. It is listed explicitly rather
            // than swept up by a broader rule because `SecurityException` is a
            // JDK type: mapping it by name alone would silently reclassify
            // anything else that ever throws one.
            case SecurityException ignored -> new TranslatedRefusal(
                    ErrorCode.ACTOR_NOT_AUTHENTICATED,
                    "no authenticated actor on the request");

            case AccessDeniedException ignored -> new TranslatedRefusal(
                    ErrorCode.ACTOR_NOT_PERMITTED,
                    "actor lacks the required permission");

            // Concurrency. Retryable, and that is the whole point of the code:
            // the write lost a race and the same request may well win the next.
            case OptimisticLockingFailureException ignored -> new TranslatedRefusal(
                    ErrorCode.RESOURCE_CONFLICT,
                    "optimistic lock conflict");

            // Matched through DuplicateKeys rather than on the Spring type
            // alone: a reactive write can surface the driver's own
            // MongoWriteException untranslated, and a translator that misses it
            // sends a uniqueness conflict down the defect path as a *retryable*
            // INTERNAL_ERROR.
            case Throwable duplicate when DuplicateKeys.isDuplicateKey(duplicate) ->
                    new TranslatedRefusal(
                            ErrorCode.RESOURCE_CONFLICT,
                            "unique index rejected the write");

            default -> null;
        });
    }
}
