package com.pml.shared.security.revocation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The revocation cache keys are shared by two processes: services write them, and the gateway's
 * session blacklist reads them before a request reaches any service. A changed format would leave
 * a revoked token accepted at the edge, so the format is pinned here.
 */
@Tag("L1")
@Tag("ET-IDN-003")
@DisplayName("Revocation keys keep the format the gateway reads")
class RevocationKeysTest {

    @Test
    @DisplayName("token, session and user keys, byte for byte")
    void formats() {
        assertThat(RevocationKeys.token("jti-1")).isEqualTo("pml:blacklist:jti-1");
        assertThat(RevocationKeys.session("sid-1")).isEqualTo("pml:session:sid-1");
        assertThat(RevocationKeys.user("sub-1")).isEqualTo("pml:revoked:sub-1");
    }

    @Test
    @DisplayName("each revocation type caches under its own key")
    void typesUseTheirKeys() {
        assertThat(RevocationType.values()).extracting(type -> type.cacheKey("x"))
                .containsExactlyInAnyOrder("pml:blacklist:x", "pml:session:x", "pml:revoked:x");
    }
}
