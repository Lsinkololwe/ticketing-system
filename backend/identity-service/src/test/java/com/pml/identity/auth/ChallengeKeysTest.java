package com.pml.identity.auth;

import com.pml.identity.auth.challenge.ChallengeKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001 · Redis key names of the challenge match CONTRACT 6 and carry only the contact key")
class ChallengeKeysTest {

    private static final String KEY = "ab12cd34";

    @Test
    @DisplayName("each key has its documented prefix")
    void prefixes() {
        assertThat(ChallengeKeys.challenge(KEY)).isEqualTo("ch:ab12cd34");
        assertThat(ChallengeKeys.attempts(KEY)).isEqualTo("ch:att:ab12cd34");
        assertThat(ChallengeKeys.lock(KEY)).isEqualTo("ch:lock:ab12cd34");
        assertThat(ChallengeKeys.cooldown(KEY)).isEqualTo("ch:cool:ab12cd34");
        assertThat(ChallengeKeys.challengeId("c-1")).isEqualTo("chid:c-1");
    }

    @Test
    @DisplayName("the keys of one contact never collide with one another")
    void distinct() {
        assertThat(java.util.Set.of(ChallengeKeys.challenge(KEY), ChallengeKeys.attempts(KEY),
                ChallengeKeys.lock(KEY), ChallengeKeys.cooldown(KEY), ChallengeKeys.challengeId(KEY))).hasSize(5);
    }
}
