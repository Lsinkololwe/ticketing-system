package com.pml.shared.error;

import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A cross-tenant id and an id that never existed answer alike.
 *
 * <h2>What is actually being asserted</h2>
 * Not that the refusal is polite. That the two responses are <b>byte-identical
 * apart from the correlation id</b>, because any difference at all is the
 * signal: a caller holding candidate ids learns which are real by diffing the
 * answers, and needs no access to anything to do it.
 */
@Tag("L1")
@Tag("ET-PLT-005")
@DisplayName("ET-PLT-005-R6 · the tenant boundary discloses nothing")
class TenantBoundaryTest {

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

    /** A refusal raised by an ordinary "no such row" lookup. */
    private static final class EventNotFound extends DomainRefusal {
        EventNotFound() {
            super(ErrorCode.EVENT_UNKNOWN, "no event with that id");
        }
    }

    @Test
    @DisplayName("a cross-tenant reach and an unissued id are indistinguishable")
    void crossTenantMatchesNotFoundExactly() {
        GraphQLError notFound = handle(new EventNotFound());
        GraphQLError crossTenant = handle(TenantBoundary.refuse(
                ErrorCode.EVENT_UNKNOWN,
                "user 8f21c in org A reached event 77 owned by org B"));

        assertThat(crossTenant.getMessage())
                .as("a different message is as good a signal as a different code")
                .isEqualTo(notFound.getMessage());

        assertThat(withoutCorrelationId(crossTenant.getExtensions()))
                .as("""
                    every extension key must match. A caller holding candidate ids reads \
                    the difference and learns which ones are real — without needing access \
                    to any of them.""")
                .isEqualTo(withoutCorrelationId(notFound.getExtensions()));
    }

    @Test
    @DisplayName("the developer message describing the reach never leaves the server")
    void theReachIsLoggedNotReturned() {
        GraphQLError refusal = handle(TenantBoundary.refuse(
                ErrorCode.ORGANIZATION_UNKNOWN,
                "user 8f21c in org A reached organization B"));

        assertThat(refusal.getMessage() + refusal.getExtensions())
                .doesNotContain("8f21c")
                .doesNotContain("org A")
                .doesNotContain("organization B");
    }

    @Test
    @DisplayName("refusing with a permission code is rejected at the call site")
    void permissionCodesAreRefused() {
        // The single most likely mistake: ACTOR_NOT_PERMITTED reads as the more
        // precise, more helpful answer and reopens the oracle in one line.
        assertThatThrownBy(() -> TenantBoundary.refuse(
                ErrorCode.ACTOR_NOT_PERMITTED, "cross-tenant reach"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tells the caller the resource exists");
    }

    @Test
    @DisplayName("every *_UNKNOWN code in the registry is usable at the boundary")
    void everyUnknownCodeIsAccepted() {
        List<ErrorCode> unknownCodes = java.util.Arrays.stream(ErrorCode.values())
                .filter(code -> code.name().endsWith("_UNKNOWN"))
                .toList();

        assertThat(unknownCodes)
                .as("the registry defines the not-found vocabulary this boundary answers in")
                .isNotEmpty();

        for (ErrorCode code : unknownCodes) {
            assertThat(TenantBoundary.refuse(code, "reach").errorCode()).isEqualTo(code);
        }
    }

    @Test
    @DisplayName("a cross-tenant refusal carries no details")
    void noDetailsSurvive() {
        // A detail naming the resource, the owning tenant or the permission
        // required restores exactly what withholding the code prevented.
        assertThat(TenantBoundary
                .refuse(ErrorCode.RESERVATION_UNKNOWN, "reach")
                .details())
                .isEmpty();
    }

    private static Map<String, Object> withoutCorrelationId(Map<String, Object> extensions) {
        Map<String, Object> copy = new java.util.LinkedHashMap<>(extensions);
        copy.remove(GraphQlErrors.CORRELATION_ID);
        return copy;
    }
}
