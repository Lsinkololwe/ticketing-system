package com.pml.identity.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.security.IdentityTenantReads;
import com.pml.identity.security.revocation.AccessRevocationService;
import com.pml.identity.security.revocation.RevocationTrigger;
import com.pml.identity.service.EventAccessService;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.PermissionResolutionService;
import com.pml.identity.web.graphql.mutation.EventAccessMutationResolver;
import com.pml.shared.security.Permission;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Revoking an event grant ends the grantee's live tokens with it: a scanner whose gate access is
 * withdrawn must not go on admitting people on a token that still has minutes to run.
 */
@Tag("L1")
@Tag("ET-IDN-003")
@DisplayName("Revoking an event grant cuts the grantee's tokens, and a refused revocation cuts nobody")
class RevokeEventAccessCutsTheGranteeTest {

    private static final String ACTOR = "organizer-1";
    private static final String GRANTEE = "scanner-7";

    private EventAccessService grants;
    private IdentityTenantReads reads;
    private OrganizationMemberService members;
    private AccessRevocationService cutter;
    private EventAccessMutationResolver resolver;

    @BeforeEach
    void wire() {
        grants = mock(EventAccessService.class);
        reads = mock(IdentityTenantReads.class);
        members = mock(OrganizationMemberService.class);
        cutter = mock(AccessRevocationService.class);
        resolver = new EventAccessMutationResolver(grants, reads, members,
                mock(PermissionResolutionService.class), cutter);

        EventAccessGrant held = new EventAccessGrant();
        held.setId("grant-1");
        held.setUserId(GRANTEE);
        held.setOrganizationId("org-1");
        held.setStatus(AccessGrantStatus.ACTIVE);
        when(reads.grantForCaller("grant-1")).thenReturn(Mono.just(held));
        when(members.requirePermission(eq(ACTOR), eq("org-1"), any(Permission.class))).thenReturn(Mono.empty());
        EventAccessGrant revoked = new EventAccessGrant();
        revoked.setId("grant-1");
        revoked.setUserId(GRANTEE);
        revoked.setStatus(AccessGrantStatus.REVOKED);
        when(grants.revoke("grant-1", "no longer on the gate team", ACTOR)).thenReturn(Mono.just(revoked));
        when(cutter.cutAccount(any(), any(), any())).thenReturn(Mono.empty());
    }

    private <T> Mono<T> asOrganizer(Mono<T> operation) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(ACTOR).build();
        return operation.contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new JwtAuthenticationToken(jwt, List.of())));
    }

    @Test
    @DisplayName("the grantee's tokens are cut with the grant, naming why")
    void granteeIsCut() {
        EventAccessGrant result = asOrganizer(resolver.revokeEventAccess("grant-1", "no longer on the gate team")).block();

        assertThat(result.getStatus()).isEqualTo(AccessGrantStatus.REVOKED);
        verify(cutter).cutAccount(GRANTEE, RevocationTrigger.GRANT_REVOKED, ACTOR);
    }

    @Test
    @DisplayName("a revocation the caller is not permitted to make cuts nobody")
    void refusedRevocationCutsNobody() {
        when(members.requirePermission(eq(ACTOR), eq("org-1"), any(Permission.class)))
                .thenReturn(Mono.error(new IllegalStateException("not permitted")));

        try {
            asOrganizer(resolver.revokeEventAccess("grant-1", "no longer on the gate team")).block();
        } catch (IllegalStateException expected) {
            // refused as intended
        }

        verify(cutter, never()).cutAccount(any(), any(), any());
    }
}
