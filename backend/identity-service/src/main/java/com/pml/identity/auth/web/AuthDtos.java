package com.pml.identity.auth.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Wire shapes of the internal auth API (CONTRACT 4). Contacts travel in bodies, never in URLs. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record ContactInput(String value, String type) {
        @Override
        public String toString() {
            return "ContactInput[type=" + type + "]";
        }
    }

    public record ChallengeRequest(ContactInput contact, String regionHint, String clientIp, String deviceId,
                                   String preferredChannel) {
        @Override
        public String toString() {
            return "ChallengeRequest[...]";
        }
    }

    public record ChallengeResponse(String challengeId, String contactType, String maskedContact, String channel,
                                    long expiresInSeconds, long resendAfterSeconds) {
    }

    public record VerifyRequest(String challengeId, String code) {
        @Override
        public String toString() {
            return "VerifyRequest[...]";
        }
    }

    public record VerifyResponse(String proof, String contactType, String maskedContact, long expiresInSeconds) {
        @Override
        public String toString() {
            return "VerifyResponse[" + contactType + "]";
        }
    }

    public record ConsentInput(String purpose, String version) {
    }

    public record EnsureRequest(String proof, String clientId, Boolean issueHandle, String displayName,
                                List<ConsentInput> consents) {
        @Override
        public String toString() {
            return "EnsureRequest[clientId=" + clientId + "]";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EnsureActiveResponse(String accountId, String status, @JsonProperty("isNew") boolean isNew,
                                       String loginHandle) {
        @Override
        public String toString() {
            return "EnsureActiveResponse[" + accountId + "]";
        }
    }

    /** 202: the account workflow is still running. {@code accountId} is null until the account exists. */
    public record EnsureProvisioningResponse(String accountId, String status, int retryAfterSeconds) {
    }

    public record RedeemRequest(String handle, String clientId) {
        @Override
        public String toString() {
            return "RedeemRequest[clientId=" + clientId + "]";
        }
    }

    public record RedeemResponse(String accountId) {
    }

    public record StatusResponse(String accountId, String status, String keycloakUserId) {
    }
}
