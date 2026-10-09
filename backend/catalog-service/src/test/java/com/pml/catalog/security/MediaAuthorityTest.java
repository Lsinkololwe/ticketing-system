package com.pml.catalog.security;

import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import com.pml.catalog.infrastructure.client.IdentityServiceClient.OrganizationMembershipInfo;
import com.pml.catalog.infrastructure.client.IdentityServiceClient.UserOrganizationsResponse;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Identity's authorization check refuses a request that names no organization ("Organization ID is
 * required"), which made every picture upload fail. The caller's organization is resolved first, then
 * asked about.
 */
@Tag("L1")
@Tag("ET-CAT-004")
@DisplayName("ET-CAT-004 · picture uploads name the caller's organization to the authorization check")
class MediaAuthorityTest {

    @Test
    void ownedOrganizationIsNamedInTheCheck() {
        IdentityServiceClient identity = mock(IdentityServiceClient.class);
        when(identity.getUserOrganizations("u1")).thenReturn(Mono.just(new UserOrganizationsResponse(List.of(
                new OrganizationMembershipInfo("org-team", "MANAGER", true),
                new OrganizationMembershipInfo("org-own", "OWNER", true)))));
        when(identity.checkAuthorization(any(AuthorizationRequest.class)))
                .thenReturn(Mono.just(AuthorizationResult.authorizedAsOwner("org-own")));

        StepVerifier.create(new MediaAuthority(identity).organizationOf("u1")).expectNext("org-own").verifyComplete();

        ArgumentCaptor<AuthorizationRequest> sent = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(identity).checkAuthorization(sent.capture());
        assertThat(sent.getValue().getOrganizationId()).isEqualTo("org-own");
    }

    @Test
    void noOrganizationIsRefused() {
        IdentityServiceClient identity = mock(IdentityServiceClient.class);
        when(identity.getUserOrganizations("u2")).thenReturn(Mono.just(new UserOrganizationsResponse(List.of())));
        StepVerifier.create(new MediaAuthority(identity).organizationOf("u2")).expectError(AccessDeniedException.class).verify();
    }
}
