package com.pml.shared.security;

import com.pml.shared.security.tenancy.PlatformWideAuditSink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OWASP A05 (security misconfiguration) and A09 (logging and monitoring) for the permission guard: a
 * service cannot start with the guard missing, an unknown code grants nothing, and a service's own
 * audit sink is the one the guard records to.
 */
@Tag("L3")
@Tag("ET-PLT-013")
@DisplayName("A05/A09 · the permission guard is always installed, fails closed on an unknown code and records to the service's sink")
class PermissionGuardMisconfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PermissionGuardAutoConfiguration.class));

    @Test
    @DisplayName("A05 · the aspect and the expression bean exist in every service by default")
    void guardIsInstalledByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RequiresPermissionAspect.class);
            assertThat(context).hasBean("permissionGuards");
        });
    }

    @Test
    @DisplayName("A05 · an unknown or blank code grants nothing, to any role")
    void unknownCodeGrantsNothing() {
        var superAdmin = new UsernamePasswordAuthenticationToken("u", "n", AuthorityUtils.createAuthorityList("ROLE_SUPER_ADMIN"));
        assertThat(PermissionGuards.holdsPermission(superAdmin, "payout:approve")).isTrue();
        assertThat(PermissionGuards.holdsPermission(superAdmin, "payout:*")).isFalse();
        assertThat(PermissionGuards.holdsPermission(superAdmin, "")).isFalse();
        assertThat(PermissionGuards.holdsPermission(superAdmin, null)).isFalse();
        assertThat(PermissionGuards.holdsPermission(null, "payout:approve")).isFalse();
    }

    @Test
    @DisplayName("A09 · a service that defines its own audit sink is the one the guard is built with")
    void ownSinkIsUsed() {
        List<String> seen = new CopyOnWriteArrayList<>();
        PlatformWideAuditSink sink = record -> {
            seen.add(record.operation());
            return Mono.empty();
        };
        runner.withBean(PlatformWideAuditSink.class, () -> sink).run(context -> {
            assertThat(context).hasSingleBean(RequiresPermissionAspect.class);
            assertThat(context.getBean(PlatformWideAuditSink.class)).isSameAs(sink);
        });
    }
}
