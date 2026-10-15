package com.pml.keycloak.eventlistener;

import com.pml.keycloak.identity.ContactOtpClient;
import com.pml.keycloak.identity.Dto;
import com.pml.keycloak.identity.IdentityApiException;
import com.pml.keycloak.identity.IdentityUnavailableException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;

/**
 * Tells identity-service when something happens to a Keycloak user, so the platform's own records
 * and token revocation list follow Keycloak.
 *
 * <p>What the person experiences: nothing. They sign in, sign out, change a password or
 * authenticator, and the page they see is never delayed or changed by this listener.</p>
 *
 * <p>What it does and promises: for the events listed in {@code SYNC_EVENTS} it sends a thin
 * message with the event id, type, user id, username, realm name, enabled and e-mail-verified
 * flags and a timestamp. It never sends names, roles, attributes, e-mail addresses or phone
 * numbers, so identity-service learns who did what and nothing else about them. Sign-outs and
 * refresh failures also carry the session id ({@code sid}); identity-service turns that into a
 * session revocation, so ending a Keycloak session ends the platform's tokens for it. Credential
 * changes carry the session id of the session that made the change so every other session of that
 * person can be cut while the current one is kept.</p>
 *
 * <p>Delivery is asynchronous and bounded: a small queue, a few retries with growing pauses, and
 * the event id as idempotency key so a retry is never counted twice. A client error (4xx, other
 * than 408 and 429) is not retried.
 * On refusal or failure (queue full, identity-service down, unexpected error) the event is
 * dropped with a log line that names the event type only, never contents, and the login or
 * logout that caused it carries on untouched.</p>
 */
public class UserSyncEventListener implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(UserSyncEventListener.class);

    static final Set<EventType> SYNC_EVENTS = Set.of(EventType.LOGIN, EventType.DELETE_ACCOUNT,
            EventType.UPDATE_PROFILE, EventType.UPDATE_EMAIL, EventType.VERIFY_EMAIL,
            EventType.LOGOUT, EventType.REFRESH_TOKEN_ERROR,
            EventType.UPDATE_PASSWORD, EventType.UPDATE_TOTP, EventType.REMOVE_TOTP);

    /**
     * Events that end an SSO session. They are forwarded with the session id ({@code sid}) so
     * identity-service can revoke every token minted for that session. Without a session id
     * they carry nothing to revoke and are dropped.
     */
    static final Set<EventType> SESSION_ENDING = Set.of(EventType.LOGOUT, EventType.REFRESH_TOKEN_ERROR);

    /**
     * Events that change what proves who the person is. They are forwarded with the session id of
     * the session that made the change, so identity-service can cut every other session and keep
     * that one, which is the person doing the change.
     */
    static final Set<EventType> CREDENTIAL_CHANGES =
            Set.of(EventType.UPDATE_PASSWORD, EventType.UPDATE_TOTP, EventType.REMOVE_TOTP);

    private final KeycloakSession session;
    private final ContactOtpClient client;
    private final Executor executor;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Backoff backoff;

    @FunctionalInterface
    public interface Backoff {
        void pause(Duration d) throws InterruptedException;
    }

    public UserSyncEventListener(KeycloakSession session, ContactOtpClient client, Executor executor,
                                 int maxAttempts, Duration initialBackoff, Backoff backoff) {
        this.session = session;
        this.client = client;
        this.executor = executor;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.backoff = backoff;
    }

    @Override
    public void onEvent(Event event) {
        try {
            if (event.getType() == null || !SYNC_EVENTS.contains(event.getType()) || event.getUserId() == null) {
                return;
            }
            boolean sessionEnding = SESSION_ENDING.contains(event.getType());
            String sid = sessionEnding ? event.getSessionId()
                    : CREDENTIAL_CHANGES.contains(event.getType()) ? credentialChangeSessionId(event) : null;
            if (sessionEnding && (sid == null || sid.isBlank())) {
                return;
            }
            String username = null;
            Boolean enabled = null;
            Boolean emailVerified = null;
            String realmName = null;
            if (event.getRealmId() != null) {
                var realm = session.realms().getRealm(event.getRealmId());
                if (realm != null) {
                    realmName = realm.getName();
                    UserModel user = session.users().getUserById(realm, event.getUserId());
                    if (user != null) {
                        username = user.getUsername();
                        enabled = user.isEnabled();
                        emailVerified = user.isEmailVerified();
                    }
                }
            }
            String id = event.getId() != null ? event.getId()
                    : deterministicId(event.getType().name(), event.getRealmId(), event.getUserId(),
                            String.valueOf(event.getTime()), event.getSessionId());
            dispatch(new Dto.SyncEvent(id, event.getType().name(), event.getUserId(), username, realmName,
                    enabled, emailVerified, event.getTime(), sid));
        } catch (RuntimeException e) {
            LOG.warn("user-sync dropped a user event: " + e.getClass().getSimpleName());
        }
    }

    /**
     * The session a credential change was made in.
     *
     * <p>A password or authenticator set during a sign-in (a new staff account's first sign-in, a
     * forced update) is reported with no session id, although the session that sign-in is creating
     * already exists. Keycloak builds the user session with the id of the sign-in's root
     * authentication session, so that id names it. Without it, identity-service sees a credential
     * change that names no session, cuts every session of the person, and ends the sign-in they have
     * just completed.</p>
     *
     * @return the event's session id, else the sign-in's session id, else {@code null}
     */
    private String credentialChangeSessionId(Event event) {
        String sid = event.getSessionId();
        if (sid != null && !sid.isBlank()) {
            return sid;
        }
        try {
            var context = session.getContext();
            var authenticationSession = context == null ? null : context.getAuthenticationSession();
            var root = authenticationSession == null ? null : authenticationSession.getParentSession();
            return root == null ? null : root.getId();
        } catch (RuntimeException unavailable) {
            // No sign-in is in progress on this thread; the change then names no session.
            return null;
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        try {
            if (event.getResourceType() != ResourceType.USER || event.getOperationType() == null) {
                return;
            }
            String path = event.getResourcePath();
            if (path == null || !path.startsWith("users/")) {
                return;
            }
            String userId = path.substring("users/".length());
            int slash = userId.indexOf('/');
            if (slash >= 0) {
                userId = userId.substring(0, slash);
            }
            String username = null;
            Boolean enabled = null;
            Boolean emailVerified = null;
            String realmName = null;
            if (event.getRealmId() != null) {
                var realm = session.realms().getRealm(event.getRealmId());
                if (realm != null) {
                    realmName = realm.getName();
                    UserModel user = session.users().getUserById(realm, userId);
                    if (user != null) {
                        username = user.getUsername();
                        enabled = user.isEnabled();
                        emailVerified = user.isEmailVerified();
                    }
                }
            }
            String type = "ADMIN_" + event.getOperationType().name();
            String id = event.getId() != null ? event.getId()
                    : deterministicId(type, event.getRealmId(), path, String.valueOf(event.getTime()), null);
            dispatch(new Dto.SyncEvent(id, type, userId, username, realmName, enabled, emailVerified,
                    event.getTime()));
        } catch (RuntimeException e) {
            LOG.warn("user-sync dropped an admin event: " + e.getClass().getSimpleName());
        }
    }

    private void dispatch(Dto.SyncEvent payload) {
        try {
            executor.execute(() -> deliver(payload));
        } catch (RuntimeException rejected) {
            LOG.warnf("user-sync queue full, dropped %s", payload.eventType());
        }
    }

    void deliver(Dto.SyncEvent payload) {
        Duration delay = initialBackoff;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                client.syncEvent(payload);
                return;
            } catch (IdentityApiException e) {
                if (!e.retryable()) {
                    LOG.warnf("user-sync %s rejected with %d, not retrying", payload.eventType(), e.httpStatus());
                    return;
                }
            } catch (IdentityUnavailableException e) {
                // retry below
            } catch (RuntimeException e) {
                LOG.warn("user-sync delivery failed unexpectedly: " + e.getClass().getSimpleName());
                return;
            }
            if (attempt == maxAttempts) {
                break;
            }
            try {
                backoff.pause(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            delay = delay.multipliedBy(2);
        }
        LOG.warnf("user-sync gave up on %s after %d attempts", payload.eventType(), maxAttempts);
    }

    static String deterministicId(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append(p == null ? "" : p).append('|');
        }
        return UUID.nameUUIDFromBytes(sb.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Override
    public void close() {
        // the executor belongs to the factory
    }
}
