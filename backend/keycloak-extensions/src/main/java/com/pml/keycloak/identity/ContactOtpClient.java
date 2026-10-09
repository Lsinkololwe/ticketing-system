package com.pml.keycloak.identity;

import java.util.Map;

/** Typed calls of CONTRACT section 4, items 1 to 4. */
public class ContactOtpClient {

    private static final String BASE = "/api/internal/auth";
    private final IdentityHttp http;

    public ContactOtpClient(IdentityHttp http) {
        this.http = http;
    }

    public Dto.ChallengeResponse challenge(Dto.ChallengeRequest request) {
        return http.post(BASE + "/challenges", request, Dto.ChallengeResponse.class).body();
    }

    public Dto.VerifyResponse verify(String challengeId, String code) {
        return http.post(BASE + "/challenges/verify", new Dto.VerifyRequest(challengeId, code),
                Dto.VerifyResponse.class).body();
    }

    /** 200 and 202 both return a body; use {@link Dto.EnsureResponse#active()} to tell them apart. */
    public Dto.EnsureResponse ensure(String proof, String clientId, boolean issueHandle) {
        return http.post(BASE + "/accounts/ensure",
                new Dto.EnsureRequest(proof, clientId, issueHandle, null, null),
                Dto.EnsureResponse.class).body();
    }

    public String redeem(String handle, String clientId) {
        Dto.RedeemResponse r = http.post(BASE + "/handles/redeem", new Dto.RedeemRequest(handle, clientId),
                Dto.RedeemResponse.class).body();
        return r == null ? null : r.accountId();
    }

    /** Fire-and-forget Keycloak to identity event; the id is the idempotency key. */
    public void syncEvent(Dto.SyncEvent event) {
        http.post("/api/internal/keycloak/sync/event", event, Void.class,
                Map.of("Idempotency-Key", event.eventId()));
    }
}
