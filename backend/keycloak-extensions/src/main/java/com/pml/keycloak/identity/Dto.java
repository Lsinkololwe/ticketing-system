package com.pml.keycloak.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Wire records of the internal REST contract (CONTRACT.md section 4). */
public final class Dto {

    private Dto() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContactRef(String value, String type) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChallengeRequest(ContactRef contact, String regionHint, String clientIp,
                                   String deviceId, String preferredChannel) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChallengeResponse(String challengeId, String contactType, String maskedContact,
                                    String channel, int expiresInSeconds, int resendAfterSeconds) {}

    public record VerifyRequest(String challengeId, String code) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VerifyResponse(String proof, String contactType, String maskedContact,
                                 int expiresInSeconds) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConsentGrant(String purpose, String version) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EnsureRequest(String proof, String clientId, boolean issueHandle,
                                String displayName, List<ConsentGrant> consents) {}

    /** {@code httpStatus} is set by the client (200 or 202), it is not part of the body. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnsureResponse(String accountId, String status, Boolean isNew, String loginHandle,
                                 Integer retryAfterSeconds) {
        public boolean active() {
            return "ACTIVE".equals(status) && accountId != null && !accountId.isBlank();
        }
    }

    public record RedeemRequest(String handle, String clientId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RedeemResponse(String accountId) {}

    /** RFC 9457 problem with the contract extensions. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Problem(Integer status, String title, String detail, String errorCode,
                          Boolean retryable, Integer retryAfterSeconds, Integer attemptsRemaining,
                          String lockedUntil) {}

    /** Payload of POST /api/internal/keycloak/sync/event (CONTRACT section 4.6); {@code sid} only on LOGOUT and REFRESH_TOKEN_ERROR. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SyncEvent(String eventId, String eventType, String userId, String username,
                            String realm, Boolean enabled, Boolean emailVerified, long timestamp,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String sid) {

        /** The eight slim fields; {@code sid} is absent from the JSON (session-ending events carry it). */
        public SyncEvent(String eventId, String eventType, String userId, String username,
                         String realm, Boolean enabled, Boolean emailVerified, long timestamp) {
            this(eventId, eventType, userId, username, realm, enabled, emailVerified, timestamp, null);
        }
    }
}
