package com.pml.keycloak.eventlistener;

import com.pml.keycloak.identity.ContactOtpClient;
import com.pml.keycloak.identity.IdentityHttp;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.keycloak.Config;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Registers the {@code user-sync} event listener and gives it the identity-service client and a
 * small background queue.
 *
 * <p>It needs the same four variables as the sign-in step (IDENTITY_BASE_URL, IDENTITY_CLIENT_ID,
 * IDENTITY_CLIENT_SECRET, KEYCLOAK_TOKEN_URL). If any is missing or blank Keycloak refuses to
 * start, naming the variable and never its value, because a listener that silently did nothing
 * would let sign-outs and credential changes go unnoticed by the platform's revocation list.</p>
 */
public class UserSyncEventListenerFactory implements EventListenerProviderFactory {

    public static final String PROVIDER_ID = "user-sync";

    private ContactOtpClient client;
    private ThreadPoolExecutor executor;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        if (client == null || executor == null) {
            return new NoOp();
        }
        return new UserSyncEventListener(session, client, executor, 5, Duration.ofMillis(500),
                d -> Thread.sleep(d.toMillis()));
    }

    @Override
    public void init(Config.Scope config) {
        start(System.getenv());
    }

    /**
     * Builds the client and queue from the given environment.
     *
     * @throws IllegalStateException naming the missing variables when a required one is blank
     */
    public void start(Map<String, String> env) {
        IdentityHttp http = IdentityHttp.requireFromEnvironment(env, Clock.systemUTC());
        this.client = new ContactOtpClient(http);
        this.executor = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500),
                r -> {
                    Thread t = new Thread(r, "user-sync");
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // nothing
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    private static final class NoOp implements EventListenerProvider {
        @Override
        public void onEvent(Event event) {
            // disabled
        }

        @Override
        public void onEvent(AdminEvent event, boolean includeRepresentation) {
            // disabled
        }

        @Override
        public void close() {
            // disabled
        }
    }
}
