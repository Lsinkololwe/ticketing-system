package com.pml.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The last line of the local-database guarantee.
 *
 * <h2>What the layer above cannot see</h2>
 * The machine-wide policy hook reads the command line and the module's
 * {@code application-local.yml}. Both of those can say {@code localhost} while an exported
 * {@code MONGODB_URI} points the service somewhere else entirely — the value never appears on a
 * command line, so no hook can catch it. Measured against the real hook: of ten routes to a
 * remote database, nine are refused and that one is admitted.
 *
 * <p>This guard closes it by checking the URI Spring actually resolved, which is by definition
 * the one the driver will dial.</p>
 *
 * <h2>The wiring assertion is not ceremony</h2>
 * This codebase has produced six components that were correct, tested and called by nothing.
 * A guard that is never invoked is worse than no guard, because the reassurance is real and the
 * protection is not — so the registration is asserted here rather than assumed.
 */
@Tag("L3")
@Tag("ET-PLT-002")
@DisplayName("a profile named 'local' has to actually be local")
class LocalDatastoreGuardTest {

    private static final String LOCAL = "mongodb://admin:pw@localhost:27017/dev_ticketing?authSource=admin";
    private static final String REMOTE = "mongodb://admin:pw@db-uat.internal.example:27017/ticketing";

    @Test
    @DisplayName("the local profile against a loopback host starts")
    void loopbackIsAccepted() {
        assertThatCode(() -> LocalDatastoreGuard.check(LOCAL, List.of("local")))
                .doesNotThrowAnyException();
        assertThatCode(() -> LocalDatastoreGuard.check(
                "mongodb://127.0.0.1:27017/x", List.of("local")))
                .doesNotThrowAnyException();
        assertThatCode(() -> LocalDatastoreGuard.check(
                "mongodb://[::1]:27017/x", List.of("local")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the local profile against a remote host refuses to start, and names the host")
    void remoteHostIsRefused() {
        assertThatThrownBy(() -> LocalDatastoreGuard.check(REMOTE, List.of("local")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db-uat.internal.example")
                .hasMessageContaining("MONGODB_URI");
    }

    @Test
    @DisplayName("every host in a seed list is checked, not just the first")
    void aRemoteSeedAmongLocalOnesIsCaught() {
        assertThatThrownBy(() -> LocalDatastoreGuard.check(
                "mongodb://localhost:27017,db-uat.internal.example:27017/x", List.of("local")))
                .as("""
                    The driver connects to whichever member answers. Checking only the first \
                    entry passes a seed list whose second host is a shared cluster.""")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db-uat.internal.example");
    }

    @Test
    @DisplayName("other profiles are left alone — production connects to a real cluster")
    void nonLocalProfilesAreUntouched() {
        assertThatCode(() -> LocalDatastoreGuard.check(REMOTE, List.of("prod")))
                .doesNotThrowAnyException();
        assertThatCode(() -> LocalDatastoreGuard.check(REMOTE, List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> LocalDatastoreGuard.check(REMOTE, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the local profile is caught alongside others, and regardless of case")
    void localAmongSeveralProfiles() {
        assertThatThrownBy(() -> LocalDatastoreGuard.check(REMOTE, List.of("local", "debug")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LocalDatastoreGuard.check(REMOTE, List.of("LOCAL")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("an absent URI is not this guard's business")
    void absentUriIsIgnored() {
        assertThatCode(() -> LocalDatastoreGuard.check(null, List.of("local")))
                .doesNotThrowAnyException();
        assertThatCode(() -> LocalDatastoreGuard.check("  ", List.of("local")))
                .doesNotThrowAnyException();
    }

    /**
     * The end-to-end shape of the gap: the property comes from the environment, not from any
     * file, which is exactly the case the policy hook cannot see.
     */
    @Test
    @DisplayName("a context on the local profile refuses to start when the resolved URI is remote")
    void theContextRefusesToStart() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                        .of(LocalDatastoreGuardAutoConfiguration.class))
                .withPropertyValues("spring.profiles.active=local",
                        LocalDatastoreGuardAutoConfiguration.MONGO_URI_PROPERTY + "=" + REMOTE)
                .run(context -> assertThat(context)
                        .as("the whole point is that the service does not come up at all")
                        .hasFailed());
    }

    @Test
    @DisplayName("the same context starts when the resolved URI is loopback")
    void theContextStartsOnLoopback() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                        .of(LocalDatastoreGuardAutoConfiguration.class))
                .withPropertyValues("spring.profiles.active=local",
                        LocalDatastoreGuardAutoConfiguration.MONGO_URI_PROPERTY + "=" + LOCAL)
                .run(context -> assertThat(context)
                        .as("a guard that refuses everything is not a guard, it is an outage")
                        .hasNotFailed());
    }

    @Test
    @DisplayName("the guard is registered, so it actually runs")
    void theGuardIsWired() throws IOException {
        Path imports = Path.of("src/main/resources/META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        assertThat(Files.readString(imports))
                .as("""
                    An unregistered auto-configuration is a guard that never runs, and this file \
                    is the only thing that registers it. Six components in this codebase have \
                    already been correct, tested and reachable from nothing.""")
                .contains("com.pml.shared.config.LocalDatastoreGuardAutoConfiguration");
    }
}
