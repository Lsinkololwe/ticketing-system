package com.pml.booking.security;

import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.shared.dto.EventSummaryDto;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** How the gate check reaches its answer, with catalog and identity replaced. No database. */
@Tag("L1")
@Tag("ET-TKT-003")
@DisplayName("The gate check asks identity for ticket:scan on the event's own organization")
class EventGateDecisionTest {

    private CatalogServiceClient catalog;
    private IdentityServiceClient identity;
    private EventGateAccess gates;

    @BeforeEach
    void setUp() {
        catalog = mock(CatalogServiceClient.class);
        identity = mock(IdentityServiceClient.class);
        EventSummaryDto event = new EventSummaryDto();
        event.setId("event-1");
        event.setOrganizationId("org-1");
        event.setOrganizerId("organizer-1");
        when(catalog.getEventById("event-1")).thenReturn(Mono.just(event));
        gates = new EventGateAccess(catalog, identity);
    }

    @Test
    @DisplayName("Identity is asked about the caller, the event and the organization catalog says owns it")
    void asksAboutTheOwningOrganization() {
        ArgumentCaptor<AuthorizationRequest> asked = ArgumentCaptor.forClass(AuthorizationRequest.class);
        when(identity.checkAuthorization(asked.capture())).thenReturn(Mono.just(AuthorizationResult.authorizedByEventGrant("event-1", "CHECK_IN")));

        as("user-1", List.of("ROLE_ORGANIZER"), gates.requireScan("event-1")).block();

        assertThat(asked.getValue().getUserId()).isEqualTo("user-1");
        assertThat(asked.getValue().getEventId()).isEqualTo("event-1");
        assertThat(asked.getValue().getOrganizationId()).isEqualTo("org-1");
        assertThat(asked.getValue().getRequiredPermission()).isEqualTo("ticket:scan");
    }

    @Test
    @DisplayName("ADMIN and SUPER_ADMIN pass on their platform role; FINANCE and SCANNER still need identity's yes")
    void platformRoles() {
        when(identity.checkAuthorization(any())).thenReturn(Mono.just(AuthorizationResult.deniedNotMember()));

        assertThat(as("admin", List.of("ROLE_ADMIN"), gates.requireScan("event-1")).block()).isNotNull();
        assertThat(as("root", List.of("ROLE_SUPER_ADMIN"), gates.requireScan("event-1")).block()).isNotNull();
        assertRefused(() -> as("finance", List.of("ROLE_FINANCE"), gates.requireScan("event-1")).block(), ErrorCode.EVENT_UNKNOWN);
        assertRefused(() -> as("scanner", List.of("ROLE_SCANNER"), gates.requireScan("event-1")).block(), ErrorCode.EVENT_UNKNOWN);
    }

    @Test
    @DisplayName("A refusal naming a role is ACTOR_NOT_PERMITTED; one naming none is an unknown event")
    void refusalsAreClassified() {
        when(identity.checkAuthorization(any())).thenReturn(Mono.just(AuthorizationResult.deniedInsufficientPermissions("ticket:scan", "VIEWER")));
        assertRefused(() -> as("viewer", List.of(), gates.requireScan("event-1")).block(), ErrorCode.ACTOR_NOT_PERMITTED);

        when(identity.checkAuthorization(any())).thenReturn(Mono.just(AuthorizationResult.denied("Organization status SUSPENDED does not permit ticket:scan")));
        assertRefused(() -> as("member", List.of(), gates.requireScan("event-1")).block(), ErrorCode.EVENT_UNKNOWN);
    }

    @Test
    @DisplayName("A blank event id, or one catalog does not know, is refused without asking identity")
    void unknownEventsNeverReachIdentity() {
        when(catalog.getEventById(anyString())).thenReturn(Mono.empty());

        assertRefused(() -> as("user-1", List.of(), gates.requireScan(" ")).block(), ErrorCode.EVENT_UNKNOWN);
        assertRefused(() -> as("user-1", List.of(), gates.requireScan("event-x")).block(), ErrorCode.EVENT_UNKNOWN);
        verify(identity, never()).checkAuthorization(any());
    }

    private static <T> Mono<T> as(String userId, List<String> roles, Mono<T> call) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(userId).build();
        return call.contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new JwtAuthenticationToken(jwt, roles.stream().map(SimpleGrantedAuthority::new).toList())));
    }

    private static void assertRefused(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainRefusal.class,
                refused -> assertThat(refused.errorCode()).isEqualTo(code));
    }
}
