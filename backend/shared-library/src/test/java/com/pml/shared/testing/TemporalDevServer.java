package com.pml.shared.testing;

import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * The platform's Temporal test container — the Temporal CLI's development server, a real frontend,
 * history and matching service over an in-memory SQLite store.
 *
 * <h2>When a test needs this and not the time-skipping environment</h2>
 * {@code TestWorkflowEnvironment} (layer 3) runs a workflow against an in-process test server and
 * skips time, which is what a saga test wants. It is not the server production talks to: it does
 * not exercise gRPC, namespace registration, the Spring Boot starter's connection properties, or a
 * worker polling a task queue over the wire. A test that asserts those — that a service's worker
 * really registers and really completes a run against a server — is layer 2 and uses this.
 *
 * <h2>Singleton, not per-class</h2>
 * Same reasoning as {@link MongoReplicaSet}: one container for the suite, reaped by Ryuk.
 * The {@code ticketing} namespace is registered at start, matching the services' default
 * {@code TEMPORAL_NAMESPACE}.
 */
public final class TemporalDevServer {

    /** The Temporal CLI image; {@code server start-dev} is the documented development server. */
    private static final DockerImageName IMAGE = DockerImageName.parse("temporalio/temporal:1.5.1");

    public static final String NAMESPACE = "ticketing";

    private static final int FRONTEND_PORT = 7233;

    private static final GenericContainer<?> CONTAINER;

    static {
        // The custom search attributes of docker-resources/temporal/self-hosted/search-attributes.conf, registered as the init job does
        // in production; without them a start carrying ProcessSearchAttributes is refused as unregistered.
        java.util.List<String> command = new java.util.ArrayList<>(
                java.util.List.of("server", "start-dev", "--ip", "0.0.0.0", "--namespace", NAMESPACE));
        for (String name : java.util.List.of(ProcessSearchAttributes.BUSINESS_ID_NAME,
                ProcessSearchAttributes.TENANT_ID_NAME, ProcessSearchAttributes.ORGANIZATION_ID_NAME,
                ProcessSearchAttributes.EVENT_ID_NAME, ProcessSearchAttributes.BUSINESS_STATUS_NAME,
                ProcessSearchAttributes.PROCESS_KIND_NAME)) {
            command.add("--search-attribute");
            command.add(name + "=Keyword");
        }
        CONTAINER = new GenericContainer<>(IMAGE)
                .withCommand(command.toArray(String[]::new))
                .withExposedPorts(FRONTEND_PORT)
                .waitingFor(Wait.forListeningPorts(FRONTEND_PORT)
                        .withStartupTimeout(Duration.ofMinutes(2)))
                .withReuse(true);
        CONTAINER.start();
    }

    private TemporalDevServer() {
    }

    /** {@code host:port} of the frontend gRPC service, the value of {@code spring.temporal.connection.target}. */
    public static String target() {
        return CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(FRONTEND_PORT);
    }

    public static GenericContainer<?> container() {
        return CONTAINER;
    }
}
