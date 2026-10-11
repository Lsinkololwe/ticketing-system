package com.pml.booking.security;

import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A payout is the organization's decision, taken by whoever holds the payout permission there.
 * Identity decides; this class only asks it the right question and denies when it cannot answer.
 * Flat methods: F-055.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("Payout access is the organization's permission decision, and fails closed")
class PayoutAccessTest {

    private IdentityServiceClient identity;
    private PayoutAccess access;

    @BeforeEach
    void setUp() {
        identity = Mockito.mock(IdentityServiceClient.class);
        access = new PayoutAccess(identity);
    }

    @Test
    @DisplayName("it asks identity for payout:request in the escrow's organization, on the event")
    void asksTheRightQuestion() {
        ArgumentCaptor<AuthorizationRequest> asked = ArgumentCaptor.forClass(AuthorizationRequest.class);
        Mockito.when(identity.checkAuthorization(asked.capture()))
                .thenReturn(Mono.just(AuthorizationResult.authorizedAsMember("org-1", "FINANCE")));

        assertThat(access.mayRequest("user-1", "org-1", "event-1").block()).isTrue();

        assertThat(asked.getValue().getUserId()).isEqualTo("user-1");
        assertThat(asked.getValue().getOrganizationId()).isEqualTo("org-1");
        assertThat(asked.getValue().getEventId()).isEqualTo("event-1");
        assertThat(asked.getValue().getRequiredPermission()).isEqualTo("payout:request");
        assertThat(asked.getValue().getOrganizationOwnerId()).isNull();
    }

    @Test
    @DisplayName("a refusal from identity is a refusal")
    void refusalIsRefusal() {
        Mockito.when(identity.checkAuthorization(Mockito.any()))
                .thenReturn(Mono.just(AuthorizationResult.deniedInsufficientPermissions("payout:request", "MANAGER")));

        assertThat(access.mayRequest("user-1", "org-1", "event-1").block()).isFalse();
    }

    @Test
    @DisplayName("an identity outage denies rather than allows")
    void outageDenies() {
        Mockito.when(identity.checkAuthorization(Mockito.any()))
                .thenReturn(Mono.error(new IllegalStateException("identity down")));

        assertThat(access.mayRequest("user-1", "org-1", "event-1").block()).isFalse();
    }

    @Test
    @DisplayName("a missing user or organization is refused without asking identity")
    void missingInputsAreRefusedWithoutALookup() {
        assertThat(access.mayRequest(null, "org-1", "event-1").block()).isFalse();
        assertThat(access.mayRequest("user-1", " ", "event-1").block()).isFalse();
        Mockito.verifyNoInteractions(identity);
    }
}
