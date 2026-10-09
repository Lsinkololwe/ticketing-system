package com.pml.identity.user;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Five root operations keep the shape their resolvers are bound with.
 *
 * <h2>Why these three, specifically</h2>
 * Three of the five are easy to get subtly wrong in a way no schema check and no ordinary test
 * would notice:
 *
 * <ul>
 *   <li>{@code updateMyProfile} takes a typed input, not a {@code JSON!} map. DGS binds by method
 *       name, so a resolver named {@code updateProfile} answers a <em>second</em> SDL field while
 *       {@code updateMyProfile} sits unbound beside it. An untyped input is how a field nobody
 *       meant to be writable becomes writable.</li>
 *   <li>{@code suspendUser} / {@code unsuspendUser} must move {@code accountStatus} and
 *       {@code active} together. Two independent code paths would drift.</li>
 *   <li>{@code myEffectivePermissions} must resolve through {@code PermissionResolutionService}.
 *       Pointing it at {@code currentUserPermissions} — which reads realm roles and ignores the
 *       {@code organizationId} argument — is an easy mistake, and it type-checks.</li>
 * </ul>
 *
 * <p>This reads source, so it catches the shape rather than the behaviour;
 * {@code AccountSuspensionTest} holds the behaviour against a real replica set.
 */
@Tag("L4")
@Tag("ET-IDN-002")
@DisplayName("F-003 · the newly-bound operations keep their shape")
class UnimplementedOperationLintTest {

    private static final Path RESOLVERS = Path.of("src/main/java/com/pml/identity/web/graphql");
    private static final Path USER_MUTATIONS = RESOLVERS.resolve("mutation/UserMutationResolver.java");
    private static final Path PERMISSION_QUERIES = RESOLVERS.resolve("query/PermissionQueryResolver.java");
    private static final Path SDL = Path.of("src/main/resources/graphql/schema.graphqls");

    /** Comments and string literals, so prose about the fix cannot satisfy a check for it. */
    private static final Pattern NOT_CODE = Pattern.compile(
            "/\\*.*?\\*/|//[^\\n]*|\"(?:\\\\.|[^\"\\\\\\n])*\"", Pattern.DOTALL);

    private static String userMutations;
    private static String permissionQueries;
    private static String sdl;

    @BeforeAll
    static void read() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(USER_MUTATIONS), "identity sources not present");
        userMutations = strip(Files.readString(USER_MUTATIONS));
        permissionQueries = strip(Files.readString(PERMISSION_QUERIES));
        sdl = Files.readString(SDL);
    }

    private static String strip(String source) {
        return NOT_CODE.matcher(source).replaceAll(match -> "");
    }

    @Test
    @DisplayName("ET-IDN-002 · the profile mutation takes a typed input, and the untyped one is gone")
    void profileInputStaysTyped() {
        assertThat(userMutations)
                .as("""
                    §4 names updateMyProfile, and DGS binds by method name — a method called \
                    updateProfile answers a different field. `@Valid` is part of the shape rather \
                    than decoration: without it the constraints on UpdateUserInput never run, and \
                    an input class whose annotations do not execute reads as protection that is \
                    not there.""")
                .contains("updateMyProfile(@Valid @InputArgument UpdateUserInput input)");
        assertThat(userMutations)
                .as("@Validated on the class is the other half of wiring Bean Validation")
                .contains("@Validated");
        assertThat(sdl)
                .as("""
                    `updateProfile(input: JSON!)` was a second profile mutation taking an \
                    unvalidated map. Reintroducing it would put an unchecked write path back \
                    beside the checked one.""")
                .doesNotContain("updateProfile(input: JSON!)");
        assertThat(sdl).contains("updateMyProfile(input: UpdateUserInput!): User!");
    }

    @Test
    @DisplayName("ET-IDN-002 · suspend and unsuspend share one transition")
    void suspensionHasOneCodePath() throws IOException {
        assertThat(userMutations)
                .as("both mutations must route through AccountService, the one place that moves "
                        + "status, accountStatus and active together - separate bodies drift, and a "
                        + "half-suspended account reads as suspended on its page and active in every list")
                .contains("accounts.suspend(id, reason, admin)")
                .contains("accounts.unsuspend(id, admin)");
        assertThat(userMutations)
                .as("the resolver must not write the status itself")
                .doesNotContain("setAccountStatus(")
                .doesNotContain("setActive(");

        String service = strip(Files.readString(Path.of("src/main/java/com/pml/identity/account/AccountService.java")));
        Matcher transition = Pattern.compile(
                        "private Mono<User> change\\(User account, AccountState state(.*?)\\n    \\}", Pattern.DOTALL)
                .matcher(service);
        assertThat(transition.find())
                .as("AccountService.change has moved or been renamed - re-point this lint")
                .isTrue();
        assertThat(transition.group(1))
                .as("the transition must write the three fields together and persist them")
                .contains("AccountStates.apply(account, state)")
                .contains("template.save(account)");
        String states = strip(Files.readString(Path.of("src/main/java/com/pml/identity/account/AccountStates.java")));
        assertThat(states)
                .contains("setStatus(state)")
                .contains("setAccountStatus(")
                .contains("setActive(");
    }

    @Test
    @DisplayName("ET-ORG-003 · effective permissions resolve through D-10's order, not realm roles")
    void effectivePermissionsUseTheResolutionService() {
        Matcher method = Pattern.compile(
                        "myEffectivePermissions\\([^)]*\\)\\s*\\{(.*?)\\n    \\}", Pattern.DOTALL)
                .matcher(permissionQueries);
        assertThat(method.find())
                .as("myEffectivePermissions has moved — re-point this lint")
                .isTrue();

        String body = method.group(1);
        assertThat(body)
                .as("""
                    O-2's near-miss. PermissionResolutionService is the one implementation of \
                    D-10's five-step order — platform role, event grant, organization role, custom \
                    permission, explicit deny. currentUserPermissions answers step one from the \
                    JWT's realm roles and ignores organizationId entirely, so substituting it \
                    would be right for a caller in one organization and quietly wrong for the rest.""")
                .contains("permissionResolutionService")
                .contains("getEffectivePermissions")
                .doesNotContain("currentUserPermissions");
    }
}
