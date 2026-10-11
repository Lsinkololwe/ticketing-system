package com.pml.booking.security;

import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.infrastructure.client.IdentityServiceClient.OrganizationMembershipInfo;
import com.pml.booking.infrastructure.client.IdentityServiceClient.UserOrganizationsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The organization an actor acts for is derived from them. A person belongs to one, so there is
 * never a choice to make; two is a data fault and is refused, never resolved by taking the first.
 * Flat methods: F-055.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("The actor's organization is derived, and never chosen between")
class ActorOrganizationResolverTest {

    private IdentityServiceClient identity;
    private ActorOrganizationResolver resolver;

    @BeforeEach
    void setUp() {
        identity = Mockito.mock(IdentityServiceClient.class);
        resolver = new ActorOrganizationResolver(identity);
    }

    private void belongsTo(OrganizationMembershipInfo... organizations) {
        Mockito.when(identity.getUserOrganizations("user-1"))
                .thenReturn(Mono.just(new UserOrganizationsResponse(List.of(organizations))));
    }

    @Test
    @DisplayName("one organization is the answer")
    void oneOrganization() {
        belongsTo(new OrganizationMembershipInfo("org-1", "ADMIN", true));

        assertThat(resolver.resolve("user-1").block()).isEqualTo("org-1");
    }

    @Test
    @DisplayName("more than one is refused, not resolved by taking the first")
    void moreThanOneIsRefused() {
        belongsTo(new OrganizationMembershipInfo("org-1", "ADMIN", true),
                new OrganizationMembershipInfo("org-2", "OWNER", true));

        assertThat(catchThrowable(() -> resolver.resolve("user-1").block()))
                .isInstanceOf(ActorOrganizationResolver.NoOrganizationException.class);
    }

    @Test
    @DisplayName("none is an error, not an empty dashboard")
    void none() {
        belongsTo();

        assertThat(catchThrowable(() -> resolver.resolve("user-1").block()))
                .isInstanceOf(ActorOrganizationResolver.NoOrganizationException.class);
    }
}
