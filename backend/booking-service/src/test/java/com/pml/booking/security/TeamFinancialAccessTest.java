package com.pml.booking.security;

import com.pml.booking.domain.model.PayoutRequest;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
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
    private com.pml.booking.service.EscrowService escrows;

    @BeforeEach
    void setUp() {
        identity = mock(IdentityServiceClient.class);
        escrows = mock(com.pml.booking.service.EscrowService.class);
        when(escrows.findById("escrow-1")).thenReturn(Mono.just(escrow(ORG)));
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
                .isInstanceOfSatisfying(DomainRefusal.class, refusal ->
                        assertThat(refusal.errorCode()).as("a member who lacks the permission is told so")
                                .isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED));
        assertThat(asked.getValue().getUserId()).isEqualTo("user-admin");
        assertThat(asked.getValue().getOrganizationId()).as("the organization comes from the escrow account").isEqualTo(ORG);
        assertThat(asked.getValue().getEventId()).isEqualTo("event-1");
        assertThat(asked.getValue().getRequiredPermission()).isEqualTo("payout:request");
        verify(process, never()).request(any(), anyString(), anyString());

        when(identity.checkAuthorization(any())).thenReturn(Mono.just(AuthorizationResult.authorizedAsMember(ORG, "ADMIN")));
        when(process.request(any(), anyString(), anyString())).thenReturn(Mono.just(new PayoutRequest()));
        as("user-admin", List.of(), resolver.createPayoutRequest(input())).block();
        verify(process).request(any(), org.mockito.ArgumentMatchers.eq("user-admin"), org.mockito.ArgumentMatchers.eq(ORG));
    }

    @Test
    @DisplayName("A payout against an escrow account of another organization reads exactly like one that does not exist")
    void anotherOrganizationsAccountIsRefusedLikeAMissingOne() {
        PayoutProcess process = mock(PayoutProcess.class);
        PayoutRequestMutationResolver resolver = resolver(process);
        when(escrows.findById("escrow-elsewhere")).thenReturn(Mono.just(escrow("org-elsewhere")));
        when(escrows.findById("escrow-missing")).thenReturn(Mono.empty());
        when(identity.checkAuthorization(any())).thenReturn(Mono.just(AuthorizationResult.deniedNotMember()));

        Throwable foreign = org.assertj.core.api.Assertions.catchThrowable(
                () -> as("user-admin", List.of(), resolver.createPayoutRequest(inputFor("escrow-elsewhere"))).block());
        Throwable missing = org.assertj.core.api.Assertions.catchThrowable(
                () -> as("user-admin", List.of(), resolver.createPayoutRequest(inputFor("escrow-missing"))).block());

        assertThat(foreign).isInstanceOf(DomainRefusal.class);
        assertThat(missing).isInstanceOf(DomainRefusal.class);
        assertThat(((DomainRefusal) foreign).errorCode()).isEqualTo(ErrorCode.ESCROW_ACCOUNT_UNKNOWN);
        assertThat(((DomainRefusal) missing).errorCode()).as("the same answer").isEqualTo(ErrorCode.ESCROW_ACCOUNT_UNKNOWN);
        verify(process, never()).request(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Who created the event does not matter: any member holding the permission may request the payout")
    void anyPermittedMemberMayRequest() {
        PayoutProcess process = mock(PayoutProcess.class);
        when(process.request(any(), anyString(), anyString())).thenReturn(Mono.just(new PayoutRequest()));
        when(identity.checkAuthorization(any())).thenReturn(Mono.just(AuthorizationResult.authorizedAsMember(ORG, "FINANCE")));

        as("someone-who-did-not-create-it", List.of(), resolver(process).createPayoutRequest(input())).block();

        verify(process).request(any(), org.mockito.ArgumentMatchers.eq("someone-who-did-not-create-it"),
                org.mockito.ArgumentMatchers.eq(ORG));
    }

    @Test
    @DisplayName("Platform finance staff acting for an organizer are held to the organizer's own authority")
    void staffAreCheckedAsTheOrganizer() {
        PayoutProcess process = mock(PayoutProcess.class);
        ArgumentCaptor<AuthorizationRequest> asked = ArgumentCaptor.forClass(AuthorizationRequest.class);
        when(identity.checkAuthorization(asked.capture())).thenReturn(Mono.just(AuthorizationResult.denied("organization pending review")));

        assertThatThrownBy(() -> as("user-finance", List.of(new SimpleGrantedAuthority("ROLE_FINANCE")),
                resolver(process).createPayoutRequest(input())).block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        refusal -> assertThat(refusal.errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED));
        assertThat(asked.getValue().getUserId()).isEqualTo(ORGANIZER);
    }

    private static com.pml.booking.domain.model.EventEscrowAccount escrow(String organizationId) {
        com.pml.booking.domain.model.EventEscrowAccount escrow = new com.pml.booking.domain.model.EventEscrowAccount();
        escrow.setId("escrow-1");
        escrow.setEventId("event-1");
        escrow.setOrganizationId(organizationId);
        return escrow;
    }

    private PayoutRequestMutationResolver resolver(PayoutProcess process) {
        return new PayoutRequestMutationResolver(mock(PayoutRequestService.class), mock(TenantReads.class),
                mock(PayoutRecoveryService.class), identity, escrows, new com.pml.booking.security.PayoutAccess(identity), process,
                com.pml.shared.testing.IdempotencyPassthrough.guard(),
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    }

    private static CreatePayoutRequestInput input() {
        return inputFor("escrow-1");
    }

    private static CreatePayoutRequestInput inputFor(String escrowAccountId) {
        return new CreatePayoutRequestInput(ORGANIZER, "event-1", escrowAccountId, "bank-1", new BigDecimal("500.00"), "ZMW",
                null, null, null, "idem-" + System.nanoTime());
    }

    private static JwtAuthenticationToken token(String userId) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "none").subject(userId).build(), List.of());
    }

    private static <T> Mono<T> as(String userId, List<SimpleGrantedAuthority> authorities, Mono<T> call) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(userId).build();
        // The caller is a member of the organization, whatever platform role they carry.
        return call.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(TenantScope.of(userId, java.util.Set.of(ORG)))))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt, authorities)));
    }
}
