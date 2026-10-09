package com.pml.keycloak.eventlistener;

import com.pml.keycloak.identity.ContactOtpClient;
import com.pml.keycloak.identity.IdentityHttp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Provider id {@code user-sync}. Fail closed: without IDENTITY_BASE_URL, IDENTITY_CLIENT_ID,
 * IDENTITY_CLIENT_SECRET and KEYCLOAK_TOKEN_URL the listener is a no-op and an ERROR is logged.
 */
public class UserSyncEventListenerFactory implements EventListenerProviderFactory {

    private static final Logger LOG = Logger.getLogger(UserSyncEventListenerFactory.class);
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
        Map<String, String> env = System.getenv();
        List<String> missing = IdentityHttp.missing(env);
        IdentityHttp http = IdentityHttp.fromEnvironment(env, Clock.systemUTC());
        if (http == null) {
            LOG.errorf("user-sync DISABLED (fail closed): missing environment %s", missing);
            return;
        }
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
