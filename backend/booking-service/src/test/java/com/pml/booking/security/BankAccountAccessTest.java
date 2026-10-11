package com.pml.booking.security;

import com.pml.shared.error.DomainRefusal;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * A payout account belongs to the organization. Who may add, see or change it is whoever holds the
 * payout permission there; everyone else is told the account does not exist. Flat methods: F-055.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("Payout accounts are managed by members holding the organization's payout permission")
class BankAccountAccessTest {

    private PayoutAccess payout;
    private BankAccountAccess access;

    @BeforeEach
    void setUp() {
        payout = Mockito.mock(PayoutAccess.class);
        access = new BankAccountAccess(payout);
    }

    private static <T> Mono<T> as(Mono<T> call, String userId, TenantScope scope) {
        var token = new JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "none").subject(userId).build(),
                AuthorityUtils.NO_AUTHORITIES);
        return call.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(token));
    }

    @Test
    @DisplayName("a member holding the payout permission may manage, whoever added the account")
    void permittedMemberMayManage() {
        Mockito.when(payout.mayRequest("member-1", "org-1", null)).thenReturn(Mono.just(true));

        assertThat(as(access.require("org-1"), "member-1", TenantScope.of("member-1", Set.of("org-1"))).block())
                .isEqualTo("org-1");
    }

    @Test
    @DisplayName("a member without the permission is refused as an unknown account")
    void memberWithoutPermissionIsRefused() {
        Mockito.when(payout.mayRequest("member-1", "org-1", null)).thenReturn(Mono.just(false));

        Throwable refused = catchThrowable(
                () -> as(access.require("org-1"), "member-1", TenantScope.of("member-1", Set.of("org-1"))).block());

        assertThat(refused).isInstanceOf(DomainRefusal.class);
        assertThat(as(access.may("org-1"), "member-1", TenantScope.of("member-1", Set.of("org-1"))).block()).isFalse();
    }

    @Test
    @DisplayName("another organization's account is refused without asking identity")
    void otherOrganizationIsRefusedWithoutALookup() {
        Throwable refused = catchThrowable(
                () -> as(access.require("org-other"), "member-1", TenantScope.of("member-1", Set.of("org-1"))).block());

        assertThat(refused).isInstanceOf(DomainRefusal.class);
        Mockito.verifyNoInteractions(payout);
    }

    @Test
    @DisplayName("platform staff manage every organization's accounts")
    void platformStaffMayManage() {
        assertThat(as(access.require("org-other"), "staff-1", TenantScope.platformAdministrator("staff-1", Set.of())).block())
                .isEqualTo("org-other");
        Mockito.verifyNoInteractions(payout);
    }
}
