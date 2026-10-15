package com.pml.booking.security;

import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * Promo codes are managed by whoever holds the promotion permission in the organization: being a
 * member is not enough, and an unanswered question is a refusal.
 */
@Tag("L1")
@Tag("ET-PLT-013")
@DisplayName("Promo codes are managed by members holding the organization's promotion permission")
class PromoCodeAccessTest {

    private IdentityServiceClient identity;
    private PromoCodeAccess access;

    @BeforeEach
    void setUp() {
        identity = Mockito.mock(IdentityServiceClient.class);
        access = new PromoCodeAccess(identity);
    }

    private static <T> Mono<T> as(Mono<T> call, String userId, TenantScope scope) {
        var token = new JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "none").subject(userId).build(),
                AuthorityUtils.NO_AUTHORITIES);
        return call.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(token));
    }

    @Test
    @DisplayName("a member holding promotion:manage may manage, and identity is asked for exactly that permission")
    void permittedMemberMayManage() {
        ArgumentCaptor<AuthorizationRequest> asked = ArgumentCaptor.forClass(AuthorizationRequest.class);
        Mockito.when(identity.checkAuthorization(asked.capture()))
                .thenReturn(Mono.just(AuthorizationResult.authorizedAsMember("org-1", "MARKETER")));

        assertThat(as(access.requireManage("org-1"), "member-1", TenantScope.of("member-1", Set.of("org-1"))).block())
                .isEqualTo("org-1");

        assertThat(asked.getValue().getRequiredPermission()).isEqualTo("promotion:manage");
        assertThat(asked.getValue().getOrganizationId()).isEqualTo("org-1");
        assertThat(asked.getValue().getUserId()).isEqualTo("member-1");
    }

    @Test
    @DisplayName("a member without the permission is refused as not permitted")
    void memberWithoutPermissionIsRefused() {
        Mockito.when(identity.checkAuthorization(Mockito.any()))
                .thenReturn(Mono.just(AuthorizationResult.deniedInsufficientPermissions("promotion:manage", "CONTRIBUTOR")));

        Throwable refused = catchThrowable(() -> as(access.requireManage("org-1"), "member-1",
                TenantScope.of("member-1", Set.of("org-1"))).block());

        assertThat(refused).isInstanceOfSatisfying(DomainRefusal.class,
                domain -> assertThat(domain.errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED));
    }

    @Test
    @DisplayName("an identity outage or an empty answer refuses; it never allows")
    void failuresRefuse() {
        Mockito.when(identity.checkAuthorization(Mockito.any())).thenReturn(Mono.error(new IllegalStateException("down")));
        assertThat(catchThrowable(() -> as(access.requireManage("org-1"), "member-1",
                TenantScope.of("member-1", Set.of("org-1"))).block())).isInstanceOf(DomainRefusal.class);

        Mockito.when(identity.checkAuthorization(Mockito.any())).thenReturn(Mono.empty());
        assertThat(catchThrowable(() -> as(access.requireManage("org-1"), "member-1",
                TenantScope.of("member-1", Set.of("org-1"))).block())).isInstanceOf(DomainRefusal.class);
    }

    @Test
    @DisplayName("a code with no organization is refused without asking identity")
    void unownedCodeIsRefused() {
        assertThat(catchThrowable(() -> as(access.requireManage(null), "member-1",
                TenantScope.of("member-1", Set.of("org-1"))).block())).isInstanceOf(DomainRefusal.class);
        Mockito.verifyNoInteractions(identity);
    }

    @Test
    @DisplayName("platform staff manage every organization's promo codes without a membership")
    void platformStaffMayManage() {
        assertThat(as(access.requireManage("org-other"), "staff-1",
                TenantScope.platformAdministrator("staff-1", Set.of())).block()).isEqualTo("org-other");
        Mockito.verifyNoInteractions(identity);
    }
}
