package com.pml.booking.security;

import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.exception.BusinessValidationException;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.infrastructure.client.IdentityServiceClient.SharedOrganizationResponse;
import com.pml.booking.service.PayoutRecoveryService;
import com.pml.booking.service.PayoutRequestService;
import com.pml.booking.web.graphql.dto.CreatePayoutRequestInput;
import com.pml.booking.web.graphql.mutation.PayoutRequestMutationResolver;
import com.pml.booking.workflow.payout.PayoutProcess;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Team members reach an organizer's financial figures and payout requests only when identity
 * grants them the permission, so the owner's switches decide rather than a hard-coded role list.
 */
@Tag("L1")
@Tag("ET-ORG-003")
@DisplayName("Financial figures and payout requests follow identity's permission decision")
class TeamFinancialAccessTest {

    private static final String ORGANIZER = "user-owner";
    private static final String ORG = "org-1";

    private IdentityServiceClient identity;
    private OrganizationSecurityService security;

    @BeforeEach
    void setUp() {
        identity = mock(IdentityServiceClient.class);
        security = new OrganizationSecurityService(identity);
        when(identity.checkSameOrganization(anyString(), anyString())).thenReturn(Mono.just(SharedOrganizationResponse.noSharedOrganization()));
        when(identity.checkSameOrganization("user-manager", ORGANIZER))
                .thenReturn(Mono.just(new SharedOrganizationResponse(true, ORG)));
    }

    @Test
    @DisplayName("A manager sees the figures exactly when identity grants financial:view in the shared organization")
    void managerFollowsIdentity() {
        ArgumentCaptor<AuthorizationRequest> asked = ArgumentCaptor.forClass(AuthorizationRequest.class);
        when(identity.checkAuthorization(asked.capture())).thenReturn(Mono.just(AuthorizationResult.denied("switch off")));

        assertThat(security.canViewFinancialData(ORGANIZER, token("user-manager")).block()).isFalse();
        assertThat(asked.getValue().getRequiredPermission()).isEqualTo("financial:view");
        assertThat(asked.getValue().getOrganizationId()).isEqualTo(ORG);
        assertThat(asked.getValue().getUserId()).isEqualTo("user-manager");

        when(identity.checkAuthorization(any())).thenReturn(Mono.just(AuthorizationResult.authorizedAsOwner(ORG)));
        assertThat(security.canViewFinancialData(ORGANIZER, token("user-manager")).block()).isTrue();
    }

    @Test
    @DisplayName("Someone outside the organization is refused without a permission lookup")
    void outsiderIsRefused() {
        assertThat(security.canViewFinancialData(ORGANIZER, token("user-stranger")).block()).isFalse();
        verify(identity, never()).checkAuthorization(any());
    }

    @Test
    @DisplayName("A payout requested by a team member is checked as that member, and refused when identity refuses")
    void teamMemberPayoutIsCheckedAsTheMember() {
        PayoutProcess process = mock(PayoutProcess.class);
        PayoutRequestMutationResolver resolver = resolver(process);
        ArgumentCaptor<AuthorizationRequest> asked = ArgumentCaptor.forClass(AuthorizationRequest.class);
        when(identity.checkAuthorization(asked.capture())).thenReturn(Mono.just(AuthorizationResult.denied("switch off")));

        assertThatThrownBy(() -> as("user-admin", List.of(), resolver.createPayoutRequest(input())).block())
                .isInstanceOf(BusinessValidationException.class);
        assertThat(asked.getValue().getUserId()).isEqualTo("user-admin");
        assertThat(asked.getValue().getOrganizationOwnerId()).isEqualTo(ORGANIZER);
        assertThat(asked.getValue().getRequiredPermission()).isEqualTo("payout:request");
        verify(process, never()).request(any(), anyString());

        when(identity.checkAuthorization(any())).thenReturn(Mono.just(AuthorizationResult.authorizedAsOwner(ORG)));
        when(process.request(any(), anyString())).thenReturn(Mono.just(new PayoutRequest()));
        as("user-admin", List.of(), resolver.createPayoutRequest(input())).block();
        verify(process).request(any(), org.mockito.ArgumentMatchers.eq("user-admin"));
    }

    @Test
    @DisplayName("Platform finance staff acting for an organizer are held to the organizer's own authority")
    void staffAreCheckedAsTheOrganizer() {
        PayoutProcess process = mock(PayoutProcess.class);
        ArgumentCaptor<AuthorizationRequest> asked = ArgumentCaptor.forClass(AuthorizationRequest.class);
        when(identity.checkAuthorization(asked.capture())).thenReturn(Mono.just(AuthorizationResult.denied("organization pending review")));

        assertThatThrownBy(() -> as("user-finance", List.of(new SimpleGrantedAuthority("ROLE_FINANCE")),
                resolver(process).createPayoutRequest(input())).block())
                .isInstanceOf(BusinessValidationException.class);
        assertThat(asked.getValue().getUserId()).isEqualTo(ORGANIZER);
    }

    private PayoutRequestMutationResolver resolver(PayoutProcess process) {
        return new PayoutRequestMutationResolver(mock(PayoutRequestService.class), mock(TenantReads.class),
                mock(PayoutRecoveryService.class), identity, process);
    }

    private static CreatePayoutRequestInput input() {
        return new CreatePayoutRequestInput(ORGANIZER, "event-1", "escrow-1", "bank-1", new BigDecimal("500.00"), "ZMW",
                null, null, null, null);
    }

    private static JwtAuthenticationToken token(String userId) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "none").subject(userId).build(), List.of());
    }

    private static <T> Mono<T> as(String userId, List<SimpleGrantedAuthority> authorities, Mono<T> call) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(userId).build();
        return call.contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt, authorities)));
    }
}
