package com.pml.booking.security;

import com.pml.booking.domain.model.Ticket;
import com.pml.booking.web.graphql.federation.TicketFieldResolver;
import com.pml.booking.web.graphql.resolver.TicketHolderContactFields;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A ticket's holder contact is personal data of someone who may not be the caller. It reaches a
 * reader only if they hold the ticket, belong to the event's organization, or administer the
 * platform (OWASP A01/A02). Flat methods: F-055.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("Ticket holder contact is answered only to the holder, the organization and administrators")
class TicketHolderContactAccessTest {

    private static final TicketHolderContactFields FIELDS = new TicketHolderContactFields();
    private static final TicketFieldResolver REFERENCE = new TicketFieldResolver();

    private static Ticket ticket() {
        Ticket ticket = new Ticket();
        ticket.setId("t-1");
        ticket.setBuyerId("holder-1");
        ticket.setOrganizationId("org-1");
        ticket.setBuyerName("Mulenga Banda");
        ticket.setBuyerEmail("mulenga@example.test");
        ticket.setBuyerPhone("+260971000001");
        return ticket;
    }

    private static DgsDataFetchingEnvironment sourced(Ticket ticket) {
        DgsDataFetchingEnvironment dfe = Mockito.mock(DgsDataFetchingEnvironment.class);
        Mockito.when(dfe.getSource()).thenReturn(ticket);
        return dfe;
    }

    private static <T> Mono<T> as(Mono<T> call, String userId, TenantScope scope) {
        Mono<T> withScope = scope == null ? call
                : call.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)));
        if (userId == null) {
            return withScope;
        }
        var token = new JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "none").subject(userId).build(),
                AuthorityUtils.NO_AUTHORITIES);
        return withScope.contextWrite(ReactiveSecurityContextHolder.withAuthentication(token));
    }

    private static boolean readsAll(String userId, TenantScope scope) {
        Ticket ticket = ticket();
        return "Mulenga Banda".equals(as(FIELDS.buyerName(sourced(ticket)), userId, scope).block())
                && "mulenga@example.test".equals(as(FIELDS.buyerEmail(sourced(ticket)), userId, scope).block())
                && "+260971000001".equals(as(FIELDS.buyerPhone(sourced(ticket)), userId, scope).block());
    }

    private static boolean readsNone(String userId, TenantScope scope) {
        Ticket ticket = ticket();
        return as(FIELDS.buyerName(sourced(ticket)), userId, scope).block() == null
                && as(FIELDS.buyerEmail(sourced(ticket)), userId, scope).block() == null
                && as(FIELDS.buyerPhone(sourced(ticket)), userId, scope).block() == null;
    }

    @Test
    @DisplayName("the holder reads their own contact, with no organization membership at all")
    void holderReads() {
        assertThat(readsAll("holder-1", TenantScope.denyAll("holder-1"))).isTrue();
    }

    @Test
    @DisplayName("a member of the event's organization reads it, and a platform administrator reads any")
    void organizationAndAdminRead() {
        assertThat(readsAll("staff-1", TenantScope.of("staff-1", Set.of("org-1")))).isTrue();
        assertThat(readsAll("admin-1", TenantScope.platformAdministrator("admin-1", Set.of()))).isTrue();
    }

    @Test
    @DisplayName("another buyer, a member of a different organization and an anonymous caller read nothing")
    void everyoneElseReadsNothing() {
        assertThat(readsNone("buyer-2", TenantScope.denyAll("buyer-2"))).as("another buyer").isTrue();
        assertThat(readsNone("staff-9", TenantScope.of("staff-9", Set.of("org-other")))).as("another organization").isTrue();
        assertThat(readsNone(null, TenantScope.denyAll("anon"))).as("anonymous").isTrue();
    }

    @Test
    @DisplayName("with no tenant scope in the context the answer is a refusal, not a pass")
    void missingScopeRefuses() {
        assertThat(readsNone("buyer-2", null)).isTrue();
        assertThat(readsAll("holder-1", null)).as("the holder needs no scope").isTrue();
    }

    @Test
    @DisplayName("the ticket's buyer reference carries cached contact only for an entitled caller")
    void federationProvidesOnlyToTheEntitled() {
        Map<String, Object> other = as(REFERENCE.getTicketBuyer(sourced(ticket())), "buyer-2", TenantScope.denyAll("buyer-2")).block();
        assertThat(other).containsOnly(Map.entry("__typename", "User"), Map.entry("id", "holder-1"));

        Map<String, Object> staff = as(REFERENCE.getTicketBuyer(sourced(ticket())), "staff-1",
                TenantScope.of("staff-1", Set.of("org-1"))).block();
        assertThat(staff).containsEntry("fullName", "Mulenga Banda").containsEntry("email", "mulenga@example.test")
                .containsEntry("phoneNumber", "+260971000001");
    }
}
