package com.pml.identity.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The round-trip tests are only as good as the samples they write: a sample must match its own pattern. */
@Tag("L1")
@Tag("ET-PLT-010")
@DisplayName("F-031 · the pattern sampler produces strings the pattern accepts")
class ValidatorPatternSamplerTest {

    @Test
    @DisplayName("the pattern families the schemas use")
    void samplesMatch() {
        for (String pattern : new String[]{
                "^[a-fA-F0-9]{24}$", "^\\+[1-9]\\d{1,14}$", "^(GATEWAY|BANK)-\\d{3}$", "^\\d{4}(-[A-Z0-9]+)?$",
                "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", "^TKT-[A-Z0-9]{8}$", "^https?://.*"}) {
            String sample = IdentityValidatorRoundTripTest.fromPattern(pattern);
            assertThat(sample).as(pattern).isNotNull();
            assertThat(sample).as("sample of " + pattern).matches(pattern);
        }
    }
}
