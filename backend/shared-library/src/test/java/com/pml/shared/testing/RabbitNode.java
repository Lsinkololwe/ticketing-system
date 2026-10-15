package com.pml.shared.testing;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * The platform's RabbitMQ test container.
 *
 * <h2>Its own broker, never the development one</h2>
 * {@code dev_rabbitmq} in docker-resources is shared with Twende. A test that declares, purges or
 * deletes queues against it could destroy another project's messages, so the suite runs its own.
 *
 * <h2>Matched to what production runs</h2>
 * The same {@code rabbitmq:4-management-alpine} image as the compose file. The version matters to what
 * is asserted: quorum queues, single active consumer and dead-lettering behave differently across
 * major versions, and a test that passed on another one would prove nothing about this one.
 *
 * <h2>Singleton, like {@link MongoReplicaSet}</h2>
 * One container for the suite, reaped by Ryuk when the JVM exits. A test that needs isolation names
 * its own exchanges and queues, or its own virtual host through {@link #newVirtualHost()}.
 */
public final class RabbitNode {

    private static final DockerImageName IMAGE = DockerImageName.parse("rabbitmq:4-management-alpine");

    private static final int AMQP_PORT = 5672;
    private static final int MANAGEMENT_PORT = 15672;

    /** Development values for a throw-away broker; not secrets. */
    public static final String USERNAME = "test";
    public static final String PASSWORD = "test-not-a-secret";

    private static final GenericContainer<?> CONTAINER;

    static {
        CONTAINER = new GenericContainer<>(IMAGE)
                .withEnv("RABBITMQ_DEFAULT_USER", USERNAME)
                .withEnv("RABBITMQ_DEFAULT_PASS", PASSWORD)
                .withExposedPorts(AMQP_PORT, MANAGEMENT_PORT)
                .waitingFor(Wait.forLogMessage(".*Server startup complete.*\\n", 1))
                .withReuse(true);
        CONTAINER.start();
    }

    private RabbitNode() {
    }

    public static String host() {
        return CONTAINER.getHost();
    }

    public static int port() {
        return CONTAINER.getMappedPort(AMQP_PORT);
    }

    /** The management HTTP API, for tests that read queue depths. */
    public static int managementPort() {
        return CONTAINER.getMappedPort(MANAGEMENT_PORT);
    }

    /** The default virtual host. */
    public static String virtualHost() {
        return "/";
    }

    public static GenericContainer<?> container() {
        return CONTAINER;
    }

    /**
     * Creates a virtual host nobody else uses and grants the test user full access to it, so a test can
     * declare the platform topology without meeting another test's queues.
     */
    public static String newVirtualHost() {
        String vhost = "t-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        try {
            run("rabbitmqctl", "add_vhost", vhost);
            run("rabbitmqctl", "set_permissions", "-p", vhost, USERNAME, ".*", ".*", ".*");
        } catch (Exception e) {
            throw new IllegalStateException("could not create virtual host " + vhost, e);
        }
        return vhost;
    }

    private static void run(String... command) throws Exception {
        var result = CONTAINER.execInContainer(command);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException(String.join(" ", command) + " failed: " + result.getStderr());
        }
    }
}
