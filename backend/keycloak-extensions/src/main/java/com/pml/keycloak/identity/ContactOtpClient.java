package com.pml.keycloak.identity;

import java.util.Map;

/**
 * The four calls the sign-in step makes to identity-service, plus the user-sync notification.
 *
 * <p>What a buyer experiences: asking for a code (challenge), typing it (verify), being recognised
 * as an existing or new account (ensure), or arriving from the app with a login handle (redeem).</p>
 *
 * <p>What it promises: this class only shapes requests and answers; it holds no state and
 * decides nothing. In particular {@code ensure} is always sent with {@code issueHandle=false}
 * from the sign-in screens, because Keycloak itself completes the sign-in and no handle is needed.
 * A handle is single-use: identity-service consumes it on the first {@code redeem}, so a replayed
 * handle is refused there.</p>
 *
 * <p>On refusal the {@link IdentityHttp} layer throws {@link IdentityApiException} (identity-service
 * said no, with a problem code) or {@link IdentityUnavailableException} (could not be reached);
 * the authenticator maps both to a message that never reveals whether an account exists.</p>
 */
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
