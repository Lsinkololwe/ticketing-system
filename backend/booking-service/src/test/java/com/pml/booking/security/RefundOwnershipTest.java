package com.pml.booking.security;

import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.service.RefundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A customer may read their own refund, by its id or by its request number, and no one else's.
 *
 * <p>{@code refundRequestByRequestId} named a method this bean did not have, so the expression
 * failed for every caller who was not ADMIN or FINANCE — the requester was refused their own refund.
 */
@Tag("L1")
@Tag("ET-FIN-004")
@DisplayName("A refund's requester reads it, by id or by request number")
class RefundOwnershipTest {

    private final RefundService refunds = mock(RefundService.class);
    private final RefundSecurityService security = new RefundSecurityService(refunds);

    private static JwtAuthenticationToken caller(String subject) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject)
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(60)).build();
        return new JwtAuthenticationToken(jwt, List.of());
    }

    private static RefundRequest requestedBy(String userId) {
        RefundRequest refund = new RefundRequest();
        refund.setRequestedBy(userId);
        return refund;
    }

    @Test
    @DisplayName("by request number: the requester yes, another customer no, an unknown number no")
    void byRequestNumber() {
        when(refunds.findByRequestId("RF-1")).thenReturn(Mono.just(requestedBy("user-1")));
        when(refunds.findByRequestId("RF-missing")).thenReturn(Mono.empty());

        assertThat(security.isRefundRequestOwnerByRequestId("RF-1", caller("user-1")).block()).isTrue();
        assertThat(security.isRefundRequestOwnerByRequestId("RF-1", caller("user-2")).block()).isFalse();
        assertThat(security.isRefundRequestOwnerByRequestId("RF-missing", caller("user-1")).block()).isFalse();
    }

    @Test
    @DisplayName("by id, and a refund with no recorded requester belongs to no caller")
    void byIdAndNoRequester() {
        when(refunds.findById("r-1")).thenReturn(Mono.just(requestedBy("user-1")));
        when(refunds.findById("r-2")).thenReturn(Mono.just(requestedBy(null)));

        assertThat(security.isRefundRequestOwner("r-1", caller("user-1")).block()).isTrue();
        assertThat(security.isRefundRequestOwner("r-2", caller("user-1")).block()).isFalse();
    }
}
