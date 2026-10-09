package com.pml.shared.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nothing about the platform's internals crosses the boundary.
 *
 * <h2>Why this is a security test and not a tidiness one</h2>
 * An error response is the cheapest reconnaissance available: it is free to
 * trigger, it needs no authentication to attempt, and it is answered by the
 * system under attack. A stack frame names the framework and its version. A
 * {@code MongoTimeoutException} names the datastore. A message containing the
 * offending id confirms the id exists, which is the enumeration oracle the
 * tenant boundary exists to close.
 *
 * <p>OWASP files this under generation of an error message containing sensitive
 * information (CWE-209), and the mitigation is exactly what
 * {@link GraphQlErrors#forDefect} does: decide what to include rather than what
 * to strip. A denylist of scary words fails to the leaking side the first time
 * someone throws an exception nobody thought of.</p>
 */
@Tag("L1")
@Tag("ET-PLT-005")
@DisplayName("ET-PLT-005-R2 · a defect tells the caller nothing about the platform")
class ErrorBoundaryLeakTest {

    /**
     * Exceptions whose type, message or trace would each give something away.
     * They are deliberately varied: the point is that the boundary does not
     * depend on recognising any of them.
     */
    private static List<Throwable> revealingFailures() {
        return List.of(
                new NullPointerException(
                        "Cannot invoke \"com.pml.booking.domain.model.Ticket.getOwnerId()\" "
                                + "because \"ticket\" is null"),
                new IllegalStateException("MongoTimeoutException: no server at 10.0.4.12:27017"),
                new SecurityException("user 8f21c admin=false attempted /internal/payouts"),
                new RuntimeException("java.sql.SQLException: relation \"users\" does not exist"),
                new Exception("Caused by: org.springframework.beans.BeanCreationException"));
    }

    @Test
    @DisplayName("no class name, message, frame or cause reaches the extensions")
    void defectExtensionsRevealNothing() {
        for (Throwable failure : revealingFailures()) {
            Map<String, Object> extensions = GraphQlErrors.forDefect(failure, "corr-1");

            String rendered = extensions.toString();

            assertThat(rendered)
                    .as("the exception's own message reached the client: %s", rendered)
                    .doesNotContain(failure.getMessage());
            assertThat(rendered)
                    .as("the exception class name reached the client")
                    .doesNotContain(failure.getClass().getSimpleName());
            assertThat(rendered)
                    .as("a package name reached the client, naming the stack in use")
                    .doesNotContain("com.pml")
                    .doesNotContain("org.springframework")
                    .doesNotContain("java.sql")
                    .doesNotContain("Mongo");
        }
    }

    @Test
    @DisplayName("every defect produces byte-identical extensions, whatever failed")
    void defectsAreIndistinguishable() {
        // The strongest form of the rule. If two different internal failures can
        // be told apart from outside, the difference is information — and an
        // attacker gets to probe until the response changes.
        List<Map<String, Object>> rendered = revealingFailures().stream()
                .map(failure -> GraphQlErrors.forDefect(failure, "corr-1"))
                .toList();

        assertThat(rendered)
                .as("two internal failures distinguishable from outside is a side channel")
                .allMatch(extensions -> extensions.equals(rendered.get(0)));
    }

    @Test
    @DisplayName("the defect message is constant, so it cannot carry the cause")
    void theDefectMessageIsConstant() {
        // A message that varies with the cause survives every attempt to strip
        // the cause itself — it is the same leak wearing different clothes.
        assertThat(GraphQlErrors.defectMessage())
                .isEqualTo(GraphQlErrors.defectMessage())
                .doesNotContainIgnoringCase("exception")
                .doesNotContainIgnoringCase("null")
                .doesNotContain("com.pml");
    }

    @Test
    @DisplayName("a defect still carries a correlation id, so support can find it")
    void theCorrelationIdSurvives() {
        // Withholding detail from the caller only works if it is recoverable by
        // someone entitled to it. Without this the exchange is not "log it
        // instead", it is "lose it".
        Map<String, Object> extensions = GraphQlErrors.forDefect(new RuntimeException("x"), "corr-42");

        assertThat(extensions).containsEntry(GraphQlErrors.CORRELATION_ID, "corr-42");
        assertThat(extensions).containsEntry(GraphQlErrors.ERROR_CODE, "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("a refusal's details cannot overwrite the contract keys")
    void detailsCannotForgeTheContract() {
        // The keys a client branches on must come from the registry, not from
        // whatever a call site happened to put in its detail map. A refusal that
        // could set retryable=true would make the client retry something the
        // registry says will fail every time.
        DomainRefusal forged = new DomainRefusal(ErrorCode.PAYMENT_DECLINED,
                "declined by provider",
                Map.of(GraphQlErrors.RETRYABLE, true,
                        GraphQlErrors.ERROR_CODE, "INTERNAL_ERROR",
                        "declineReason", "INSUFFICIENT_FUNDS")) {
        };

        Map<String, Object> extensions = GraphQlErrors.forRefusal(forged, "corr-1");

        assertThat(extensions)
                .containsEntry(GraphQlErrors.ERROR_CODE, ErrorCode.PAYMENT_DECLINED.name())
                .containsEntry(GraphQlErrors.RETRYABLE, ErrorCode.PAYMENT_DECLINED.retryable())
                .as("a legitimate detail is still carried through")
                .containsEntry("declineReason", "INSUFFICIENT_FUNDS");
    }
}
