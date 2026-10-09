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
 * Tells identity-service that a Keycloak user changed (CONTRACT section 4.6). The payload is
 * deliberately thin: ids, flags and a timestamp. No names, roles, attributes, e-mail or phone.
 * LOGOUT and REFRESH_TOKEN_ERROR also carry the session id ({@code sid}), which identity-service
 * turns into a session revocation: Keycloak logout is an input of the platform's own revocation list.
 *
 * <p>Delivery is asynchronous and bounded, with the event id as idempotency key, and can never
 * block or fail a login: every error is caught and logged without payload content.</p>
 */
public class UserSyncEventListener implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(UserSyncEventListener.class);

    static final Set<EventType> SYNC_EVENTS = Set.of(EventType.LOGIN, EventType.DELETE_ACCOUNT,
            EventType.UPDATE_PROFILE, EventType.UPDATE_EMAIL, EventType.VERIFY_EMAIL,
            EventType.LOGOUT, EventType.REFRESH_TOKEN_ERROR);

    /**
     * Events that end an SSO session. They are forwarded with the session id ({@code sid}) so
     * identity-service can revoke every token minted for that session (ET-IDN-003 R7). Without a
     * session id they carry nothing to revoke and are dropped.
     */
    static final Set<EventType> SESSION_ENDING = Set.of(EventType.LOGOUT, EventType.REFRESH_TOKEN_ERROR);

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
            String sid = sessionEnding ? event.getSessionId() : null;
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
