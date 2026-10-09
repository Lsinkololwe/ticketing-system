package com.pml.identity.auth.challenge;

/** Redis key names of the challenge (CONTRACT 6). None contains a raw contact value. */
public final class ChallengeKeys {

    public static final String CHALLENGE_ID_PREFIX = "chid:";

    private ChallengeKeys() {
    }

    public static String challenge(String contactKey) {
        return "ch:" + contactKey;
    }

    public static String attempts(String contactKey) {
        return "ch:att:" + contactKey;
    }

    public static String lock(String contactKey) {
        return "ch:lock:" + contactKey;
    }

    public static String cooldown(String contactKey) {
        return "ch:cool:" + contactKey;
    }

    public static String challengeId(String challengeId) {
        return CHALLENGE_ID_PREFIX + challengeId;
    }
}
