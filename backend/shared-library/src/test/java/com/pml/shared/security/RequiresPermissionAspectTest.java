package com.pml.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.pml.shared.security.tenancy.PlatformWideAccess;
import com.pml.shared.security.tenancy.PlatformWideAuditRecord;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The permission guard around a real proxied bean: the method is not assembled or invoked for a
 * caller whose roles do not grant the permission, and both reactive return shapes are honoured.
 */
@Tag("L1")
@Tag("ET-PLT-013")
@DisplayName("@RequiresPermission refuses callers whose platform roles do not grant the permission")
class RequiresPermissionAspectTest {

    static class Operations {
        final AtomicInteger invoked = new AtomicInteger();

        @RequiresPermission(Permission.PAYOUT_APPROVE)
        public Mono<String> approve() {
            invoked.incrementAndGet();
            return Mono.just("approved");
        }

        @RequiresPermission(Permission.AUDIT_VIEW)
        public Flux<String> audit() {
            invoked.incrementAndGet();
            return Flux.just("a", "b");
        }

        @RequiresPermission(Permission.PLATFORM_CONFIGURE)
        public String notReactive() {
            return "x";
        }
    }

    private final Operations target = new Operations();
    private final Operations guarded = proxy(target);

    private static Operations proxy(Operations target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresPermissionAspect());
        return factory.getProxy();
    }

    private static <T> Mono<T> as(Mono<T> call, String... authorities) {
        return call.contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new UsernamePasswordAuthenticationToken("u", "n", AuthorityUtils.createAuthorityList(authorities))));
    }

    @Test
    @DisplayName("a role that grants the permission reaches the method, for Mono and for Flux")
    void grantedRoleProceeds() {
        assertThat(as(guarded.approve(), "ROLE_FINANCE").block()).isEqualTo("approved");
        assertThat(as(guarded.audit().collectList(), "ROLE_ADMIN").block()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("a role that does not grant it is refused and the method is never invoked")
    void otherRoleIsRefusedBeforeTheMethodRuns() {
        assertThatThrownBy(() -> as(guarded.approve(), "ROLE_ORGANIZER", "ROLE_CUSTOMER").block())
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("payout:approve");
        assertThatThrownBy(() -> as(guarded.audit().collectList(), "ROLE_ORGANIZER").block())
                .isInstanceOf(AccessDeniedException.class);
        assertThat(target.invoked.get()).isZero();
    }

    @Test
    @DisplayName("finance is refused what only the administrator holds, and a bare or lower-case role name counts for nothing")
    void rolesAreReadExactly() {
        assertThatThrownBy(() -> as(guarded.approve(), "FINANCE", "role_finance", "ROLE_finance").block())
                .isInstanceOf(AccessDeniedException.class);
        assertThat(target.invoked.get()).isZero();
    }

    @Test
    @DisplayName("no authentication at all is refused as unauthenticated")
    void anonymousIsRefused() {
        assertThatThrownBy(() -> guarded.approve().block())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThat(target.invoked.get()).isZero();
    }

    @Test
    @DisplayName("an annotation on a method that is not reactive fails loudly instead of guarding nothing")
    void nonReactiveMethodFails() {
        assertThatThrownBy(guarded::notReactive).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("needs a Mono or Flux");
    }

    @Test
    @DisplayName("a platform role's allow is recorded with the permission, the role and the actor; a refusal records nothing")
    void platformAllowIsAudited() {
        List<PlatformWideAuditRecord> recorded = new CopyOnWriteArrayList<>();
        Clock fixed = Clock.fixed(Instant.parse("2026-10-15T08:00:00Z"), ZoneOffset.UTC);
        AspectJProxyFactory factory = new AspectJProxyFactory(new Operations());
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresPermissionAspect(record -> {
            recorded.add(record);
            return Mono.empty();
        }, "identity-service", fixed));
        Operations audited = factory.getProxy();

        assertThat(as(audited.approve(), "ROLE_FINANCE").block()).isEqualTo("approved");
        assertThatThrownBy(() -> as(audited.approve(), "ROLE_ORGANIZER").block()).isInstanceOf(AccessDeniedException.class);

        assertThat(recorded).hasSize(1);
        PlatformWideAuditRecord only = recorded.get(0);
        assertThat(only.reason()).isEqualTo(PlatformWideAccess.Reason.PLATFORM_PERMISSION_EXERCISED);
        assertThat(only.operation()).isEqualTo("payout:approve");
        assertThat(only.role()).isEqualTo("ROLE_FINANCE");
        assertThat(only.actorSub()).isEqualTo("u");
        assertThat(only.service()).isEqualTo("identity-service");
        assertThat(only.at()).isEqualTo(Instant.parse("2026-10-15T08:00:00Z"));
    }

    @Test
    @DisplayName("a sink that fails does not block the operation")
    void failingSinkDoesNotBlock() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new Operations());
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresPermissionAspect(record -> Mono.error(new IllegalStateException("store down")),
                "booking-service", Clock.systemUTC()));
        Operations audited = factory.getProxy();

        assertThat(as(audited.approve(), "ROLE_ADMIN").block()).isEqualTo("approved");
    }
}
