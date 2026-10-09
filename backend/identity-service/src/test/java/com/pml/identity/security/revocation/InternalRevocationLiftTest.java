package com.pml.identity.security.revocation;

import com.pml.shared.security.revocation.RevocationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** An operator can lift a user-level revocation that would otherwise lock the person out until it expires. */
@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · a revocation can be lifted by type and value")
class InternalRevocationLiftTest {

    @Test
    void deleteLiftsTheRevocation() {
        MongoRevocationStore store = mock(MongoRevocationStore.class);
        when(store.lift(RevocationType.USER, "kc-sub-1")).thenReturn(Mono.empty());

        WebTestClient.bindToController(new InternalRevocationController(store)).build()
                .delete().uri("/api/internal/revocations/USER/kc-sub-1").exchange()
                .expectStatus().isNoContent();

        verify(store).lift(RevocationType.USER, "kc-sub-1");
    }
}
