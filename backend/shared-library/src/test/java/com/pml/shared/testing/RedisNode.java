package com.pml.shared.testing;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * The platform's Redis test container.
 *
 * <h2>Its own instance, never the development one</h2>
 * {@code docker-resources/docker-compose.yml} runs one {@code dev_redis} shared by Twende-Ride,
 * the ticketing system and Delight Store. The property under test is that a mid-suite
 * {@code FLUSHALL} loses no business data — and a {@code FLUSHALL} against that container would
 * take two other projects' data with it. A test that can destroy something outside its own
 * fixture is not a test anybody will keep running.
 *
 * <h2>Matched to what production runs</h2>
 * The same {@code redis:7-alpine} and the same {@code --appendonly yes} as the compose file. The
 * persistence flag matters to what is being asserted: AOF means Redis survives a restart, so a
 * service that wrongly kept business state here would look fine indefinitely. Only a flush
 * distinguishes a cache from a database, which is precisely why the test performs one.
 *
 * <h2>Singleton, like {@link MongoReplicaSet}</h2>
 * One container for the suite, reaped by Ryuk when the JVM exits.
 */
public final class RedisNode {

    private static final DockerImageName IMAGE = DockerImageName.parse("redis:7-alpine");

    private static final int PORT = 6379;

    private static final GenericContainer<?> CONTAINER;

    static {
        CONTAINER = new GenericContainer<>(IMAGE)
                .withExposedPorts(PORT)
                .withCommand("redis-server", "--appendonly", "yes")
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1))
                .withReuse(true);
        CONTAINER.start();
    }

    private RedisNode() {
    }

    public static String host() {
        return CONTAINER.getHost();
    }

    public static int port() {
        return CONTAINER.getMappedPort(PORT);
    }

    public static GenericContainer<?> container() {
        return CONTAINER;
    }
}
