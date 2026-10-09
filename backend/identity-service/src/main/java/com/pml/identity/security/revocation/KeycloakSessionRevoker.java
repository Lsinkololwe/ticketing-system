package com.pml.identity.security.revocation;

import com.pml.shared.security.revocation.RevocationIdentifier;
import com.pml.shared.security.revocation.RevocationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Turns a Keycloak session-ending event into a {@link RevocationType#SESSION} revocation: the
 * primary owner of "Keycloak logout revokes that {@code sid}" (ET-IDN-003 R7).
 *
 * <p>The event comes from the {@code user-sync} listener, so it covers every way a session ends in
 * Keycloak (the app's RP-initiated logout, the console, an admin, another client, the mobile app)
 * whether or not a web app is there to receive a back-channel logout. The write is the same
 * idempotent upsert every other revocation uses (document id {@code SESSION:{sid}}): a
 * redelivery, or the web app's own revocation of the same {@code sid}, overwrites rather than
 * duplicates. Nothing is ever deleted here, and only the opaque session id and the Keycloak user
 * id are involved, so there is no personal data to log.</p>
 */
@Slf4j
@Component
public class KeycloakSessionRevoker {

    /** The Keycloak event types that end a session, with the reason recorded on the revocation. */
    public static final String LOGOUT = "LOGOUT";
    public static final String REFRESH_TOKEN_ERROR = "REFRESH_TOKEN_ERROR";
    private static final Set<String> SESSION_EVENTS = Set.of(LOGOUT, REFRESH_TOKEN_ERROR);

    private final ObjectProvider<MongoRevocationStore> store;

    public KeycloakSessionRevoker(ObjectProvider<MongoRevocationStore> store) {
        this.store = store;
    }

    /** Whether the Keycloak event type ends a session. */
    public static boolean endsSession(String eventType) {
        return eventType != null && SESSION_EVENTS.contains(eventType);
    }

    /**
     * Revokes the session.
     *
     * @return the number of revocations written: {@code 1}, or {@code 0} when there is no
     *         {@code sid} to revoke; an error (so the listener retries) when the durable write fails
     *         or revocation is switched off
     */
    public Mono<Integer> revoke(String eventType, String sid, String realm) {
        if (sid == null || sid.isBlank()) {
            return Mono.just(0);
        }
        MongoRevocationStore revocations = store.getIfAvailable();
        if (revocations == null) {
            log.error("Token revocation is switched off; Keycloak {} of session {} cannot be recorded",
                    eventType, new RevocationIdentifier(RevocationType.SESSION, sid).masked());
            return Mono.error(new IllegalStateException("token revocation is switched off"));
        }
        String reason = LOGOUT.equals(eventType) ? "keycloak-logout" : "keycloak-refresh-token-error";
        String revokedBy = "keycloak:" + (realm == null || realm.isBlank() ? "myticketzm" : realm);
        return revocations.revoke(RevocationType.SESSION, sid, reason, revokedBy).thenReturn(1);
    }
}
