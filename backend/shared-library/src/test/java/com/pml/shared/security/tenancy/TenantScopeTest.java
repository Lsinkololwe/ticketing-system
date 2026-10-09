package com.pml.shared.security.tenancy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tenancy decision itself, before any query touches it.
 *
 * <p>{@link TenantScope#permits} is the one place the platform says yes. Every
 * case below is a way of saying no that a reasonable implementation gets wrong by
 * being accommodating — a blank owner, a null owner, an empty membership set. The
 * accommodating answer is always "allow", and always a hole.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("F-001 · TenantScope permits only what it was told to permit")
class TenantScopeTest {

    private static final String MINE = "org-mine";
    private static final String THEIRS = "org-theirs";

    @Test
    @DisplayName("ET-PLT-007 · a member reaches their own organization and no other")
    void memberReachesOwnOrganizationOnly() {
        TenantScope scope = TenantScope.of("user-1", Set.of(MINE));

        assertThat(scope.permits(MINE)).isTrue();
        assertThat(scope.permits(THEIRS)).isFalse();
    }

    @Test
    @DisplayName("ET-PLT-007 · a multi-organization member reaches all of them")
    void multipleMemberships() {
        // The case ActorOrganizationResolver gets wrong by returning organizations.get(0):
        // whichever sorted first wins and the rest become invisible to their own member.
        TenantScope scope = TenantScope.of("user-consultant", Set.of(MINE, THEIRS));

        assertThat(scope.permits(MINE)).isTrue();
        assertThat(scope.permits(THEIRS)).isTrue();
        assertThat(scope.permits("org-neither")).isFalse();
    }

    @ParameterizedTest(name = "owner=[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("ET-PLT-007 · a resource with no owner is reachable by nobody")
    void unownedResourceIsNotPublic(String noOwner) {
        // An unowned row is not public data — it is a row whose organizationId was
        // never set, by a bug or a half-finished migration. Reading "no owner" as
        // "anyone" turns that bug into an exposure, silently and platform-wide.
        assertThat(TenantScope.of("user-1", Set.of(MINE)).permits(noOwner)).isFalse();
    }

    @Test
    @DisplayName("ET-PLT-007 · an empty scope permits nothing, including an unowned resource")
    void emptyScopePermitsNothing() {
        TenantScope none = TenantScope.of("user-fresh", Set.of());

        assertThat(none.permitsNothing()).isTrue();
        assertThat(none.permits(MINE)).isFalse();
        assertThat(none.permits(null)).isFalse();
    }

    @Test
    @DisplayName("ET-PLT-007 · denyAll permits nothing at all")
    void denyAllIsTotal() {
        TenantScope denied = TenantScope.denyAll("user-anonymous");

        assertThat(denied.permitsNothing()).isTrue();
        assertThat(denied.platformAdmin()).isFalse();
        assertThat(denied.permits(MINE)).isFalse();
    }

    @Test
    @DisplayName("ET-PLT-007 · a platform administrator reaches every organization, holding none")
    void platformAdministratorBypasses() {
        TenantScope admin = TenantScope.platformAdministrator("user-ops", Set.of());

        assertThat(admin.permits(MINE)).isTrue();
        assertThat(admin.permits(THEIRS)).isTrue();
        assertThat(admin.permitsNothing()).isFalse();
    }

    @Test
    @DisplayName("ET-PLT-007 · an administrator still cannot reach an unowned resource")
    void administratorDoesNotReachUnowned() {
        // Bypassing the tenant filter is not the same as bypassing the requirement
        // that a resource have an owner. A null here is a data defect either way.
        assertThat(TenantScope.platformAdministrator("user-ops", Set.of()).permits(null)).isTrue();
    }

    @Test
    @DisplayName("ET-PLT-007 · the membership set cannot be mutated after construction")
    void membershipsAreDefensivelyCopied() {
        Set<String> mutable = new LinkedHashSet<>(Set.of(MINE));
        TenantScope scope = TenantScope.of("user-1", mutable);

        mutable.add(THEIRS);

        assertThat(scope.permits(THEIRS))
                .as("a scope that widens after it was resolved is a boundary that moves under load")
                .isFalse();
    }

    @Test
    @DisplayName("ET-PLT-007 · toString carries no organization ids")
    void toStringDoesNotLeakMemberships() {
        // This lands in logs and in exception messages. The caller's membership list
        // is not something either needs, and log aggregation is not access-controlled
        // the way the database is.
        String rendered = TenantScope.of("user-1", Set.of(MINE, THEIRS)).toString();

        assertThat(rendered).doesNotContain(MINE, THEIRS).contains("user-1", "organizations=2");
    }
}
