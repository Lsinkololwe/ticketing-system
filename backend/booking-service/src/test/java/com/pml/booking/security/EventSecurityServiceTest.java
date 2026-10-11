package com.pml.booking.security;

import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.service.EscrowService;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An event's money belongs to its organization, so who may read it is decided by organization
 * membership and not by whether the caller is the person who created the event. Flat methods: F-055.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("Event and escrow access follow the caller's organizations")
class EventSecurityServiceTest {

    private EscrowService escrows;
    private EventSecurityService security;

    @BeforeEach
    void setUp() {
        escrows = Mockito.mock(EscrowService.class);
        security = new EventSecurityService(escrows);
        EventEscrowAccount escrow = new EventEscrowAccount();
        escrow.setEventId("event-1");
        escrow.setOrganizationId("org-1");
        // No organizerId at all: an escrow is opened from the event-published envelope, by organization.
        Mockito.when(escrows.findByEventId("event-1")).thenReturn(Mono.just(escrow));
        Mockito.when(escrows.findByAccountNumber("ESC-1")).thenReturn(Mono.just(escrow));
        Mockito.when(escrows.findByEventId("event-missing")).thenReturn(Mono.empty());
    }

    private static Authentication token(String userId) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(userId).build();
        return new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES);
    }

    private static <T> Mono<T> scoped(Mono<T> call, TenantScope scope) {
        return call.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)));
    }

    @Test
    @DisplayName("a member of the organization may read the event's money, whoever created the event")
    void memberMayRead() {
        TenantScope scope = TenantScope.of("member-1", Set.of("org-1"));

        assertThat(scoped(security.isEventOrganizer("event-1", token("member-1")), scope).block()).isTrue();
        assertThat(scoped(security.isEscrowOwner("ESC-1", token("member-1")), scope).block()).isTrue();
    }

    @Test
    @DisplayName("someone from another organization is refused")
    void otherOrganizationIsRefused() {
        TenantScope scope = TenantScope.of("stranger-1", Set.of("org-other"));

        assertThat(scoped(security.isEventOrganizer("event-1", token("stranger-1")), scope).block()).isFalse();
        assertThat(scoped(security.isEscrowOwner("ESC-1", token("stranger-1")), scope).block()).isFalse();
    }

    @Test
    @DisplayName("an event with no escrow, an unauthenticated caller and a missing scope are all refused")
    void everythingElseIsRefused() {
        TenantScope scope = TenantScope.of("member-1", Set.of("org-1"));

        assertThat(scoped(security.isEventOrganizer("event-missing", token("member-1")), scope).block()).isFalse();
        assertThat(security.isEventOrganizer("event-1", null).block()).isFalse();
        assertThat(security.isEventOrganizer("event-1", token("member-1")).block())
                .as("no tenant scope seeded: refused, never allowed").isFalse();
    }
}
