package com.pml.shared.error;

import com.pml.shared.security.revocation.TokenRevokedException;
import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exceptions the platform did not author still get registry codes.
 *
 * <h2>What these protect</h2>
 * The translation layer is the difference between a lock conflict arriving as a
 * retryable {@code RESOURCE_CONFLICT} and arriving as {@code INTERNAL_ERROR}. A
 * client cannot tell the second apart from a real outage, so it stops retrying
 * something that would have succeeded — and nothing about that failure looks
 * broken in a log.
 */
@Tag("L1")
@Tag("ET-PLT-005")
@DisplayName("ET-PLT-005-R3 · foreign exceptions carry registry codes")
class RefusalTranslationTest {

    private final PlatformDataFetcherExceptionHandler handler =
            new PlatformDataFetcherExceptionHandler(List.of(new PlatformRefusalTranslator()));

    private GraphQLError handle(Throwable thrown) {
        return handler
                .handleException(DataFetcherExceptionHandlerParameters
                        .newExceptionParameters().exception(thrown).build())
                .join()
                .getErrors()
                .get(0);
    }

    private Object codeOf(Throwable thrown) {
        return handle(thrown).getExtensions().get(GraphQlErrors.ERROR_CODE);
    }

    @Test
    @DisplayName("a lock conflict is a retryable RESOURCE_CONFLICT, not an internal error")
    void lockConflictIsRetryable() {
        Map<String, Object> extensions =
                handle(new OptimisticLockingFailureException("version 4 != 5")).getExtensions();

        assertThat(extensions).containsEntry(GraphQlErrors.ERROR_CODE,
                ErrorCode.RESOURCE_CONFLICT.name());
        assertThat(extensions)
                .as("the client decides whether to offer retry from this field alone")
                .containsEntry(GraphQlErrors.RETRYABLE, true);
    }

    @Test
    @DisplayName("security failures separate 'who are you' from 'you may not'")
    void authenticationAndAuthorisationAreDistinct() {
        assertThat(codeOf(new AccessDeniedException("nope")))
                .isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED.name());
        assertThat(codeOf(new TokenRevokedException()))
                .as("""
                    TOKEN_REVOKED must not collapse into ACTOR_NOT_PERMITTED: the client \
                    signs out on the first and retrying the second is harmless""")
                .isEqualTo(ErrorCode.TOKEN_REVOKED.name());
    }

    @Test
    @DisplayName("IllegalArgumentException stays a defect and leaks nothing")
    void illegalArgumentIsNotAClientError() {
        // Mapping this to BAD_REQUEST would blame the caller for a server-side
        // invariant and suppress the alert that a bug exists.
        GraphQLError error = handle(new IllegalArgumentException("tierId must not be null"));

        assertThat(error.getExtensions())
                .containsEntry(GraphQlErrors.ERROR_CODE, ErrorCode.INTERNAL_ERROR.name());
        assertThat(error.getMessage() + error.getExtensions())
                .doesNotContain("tierId");
    }

    @Test
    @DisplayName("an earlier translator wins, so a service can be more specific than the platform")
    void serviceTranslatorTakesPrecedence() {
        // The ordering the idempotency mapping depends on: a duplicate key on the idempotency index
        // is not retryable, while any other duplicate key is. Retrying a reused
        // idempotency key is the double-charge the key exists to prevent.
        RefusalTranslator moreSpecific = throwable ->
                throwable instanceof OptimisticLockingFailureException
                        ? Optional.of(new TranslatedRefusal(
                                ErrorCode.IDEMPOTENCY_KEY_REUSED, "idempotency key replayed"))
                        : Optional.empty();

        PlatformDataFetcherExceptionHandler chained = new PlatformDataFetcherExceptionHandler(
                List.of(moreSpecific, new PlatformRefusalTranslator()));

        Map<String, Object> extensions = chained
                .handleException(DataFetcherExceptionHandlerParameters.newExceptionParameters()
                        .exception(new OptimisticLockingFailureException("clash"))
                        .build())
                .join()
                .getErrors()
                .get(0)
                .getExtensions();

        assertThat(extensions).containsEntry(GraphQlErrors.ERROR_CODE,
                ErrorCode.IDEMPOTENCY_KEY_REUSED.name());
        assertThat(extensions)
                .as("a reused idempotency key must never be advertised as retryable")
                .containsEntry(GraphQlErrors.RETRYABLE, false);
    }

    @Test
    @DisplayName("a translator that throws does not take the original failure down with it")
    void brokenTranslatorDegradesToDefect() {
        RefusalTranslator broken = throwable -> {
            throw new IllegalStateException("translator bug");
        };

        PlatformDataFetcherExceptionHandler chained =
                new PlatformDataFetcherExceptionHandler(List.of(broken, new PlatformRefusalTranslator()));

        Map<String, Object> extensions = chained
                .handleException(DataFetcherExceptionHandlerParameters.newExceptionParameters()
                        .exception(new OptimisticLockingFailureException("clash"))
                        .build())
                .join()
                .getErrors()
                .get(0)
                .getExtensions();

        assertThat(extensions)
                .as("the surviving translator still answers; a bug in one does not blank the chain")
                .containsEntry(GraphQlErrors.ERROR_CODE, ErrorCode.RESOURCE_CONFLICT.name());
    }

    @Test
    @DisplayName("a refusal's developer message never reaches the client")
    void developerMessageStaysServerSide() {
        // The exact leak the previous error path shipped: a message assembled
        // from data, returned verbatim. Anything that starts sending
        // getMessage() again fails here.
        DomainRefusal chatty = new TranslatedRefusal(
                ErrorCode.USER_UNKNOWN,
                "no user with email accountant@zanaco.co.zm in tenant 8f21c");

        GraphQLError error = handle(chatty);

        assertThat(error.getMessage() + error.getExtensions())
                .doesNotContain("accountant@zanaco.co.zm")
                .doesNotContain("8f21c");
        assertThat(error.getMessage()).isEqualTo(ErrorCode.USER_UNKNOWN.name());
    }
}
