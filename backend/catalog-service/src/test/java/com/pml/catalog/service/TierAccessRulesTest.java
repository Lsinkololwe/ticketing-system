package com.pml.catalog.service;

import com.pml.catalog.domain.model.TicketTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-CAT-004")
@DisplayName("ET-CAT-004-R6 · an access code is compared whole, in either case, and a tier with none opens for nobody")
class TierAccessRulesTest {

    private static TicketTier tier(String code) {
        return TicketTier.builder().accessCode(code).build();
    }

    @Test
    @DisplayName("case and surrounding spaces do not matter")
    void caseInsensitive() {
        assertThat(TierAccessService.matches(tier("Backstage-24"), TierAccessService.normalise("  backstage-24 "))).isTrue();
    }

    @Test
    @DisplayName("a prefix, an extension and a different code do not match")
    void exactOnly() {
        assertThat(TierAccessService.matches(tier("BACKSTAGE"), TierAccessService.normalise("BACK"))).isFalse();
        assertThat(TierAccessService.matches(tier("BACKSTAGE"), TierAccessService.normalise("BACKSTAGE1"))).isFalse();
        assertThat(TierAccessService.matches(tier("BACKSTAGE"), TierAccessService.normalise("FRONT"))).isFalse();
    }

    @Test
    @DisplayName("a tier with no code opens for nobody, not even an empty attempt")
    void noCode() {
        assertThat(TierAccessService.matches(tier(null), "")).isFalse();
        assertThat(TierAccessService.matches(tier(null), "ANYTHING")).isFalse();
    }
}
