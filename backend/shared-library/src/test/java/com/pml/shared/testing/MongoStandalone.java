package com.pml.shared.testing;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * A deliberately <em>standalone</em> {@code mongod} — no replica set.
 *
 * <h2>This container exists to fail</h2>
 * It is not a fallback for {@link MongoReplicaSet} and no production-shaped test
 * should use it. Its only job is to make the inert-transaction trap demonstrable: a harness
 * that cannot show the difference between a replica set and a standalone cannot
 * prove that a transaction is real. An assertion nobody has watched fail is an
 * assumption.
 *
 * <p>{@code TransactionRealityTest} runs the same two-document rollback against
 * both containers. Here it does not hold — and that is the point.
 *
 * <p>No {@code withReuse}: this is touched by one test class, and a reused
 * container would keep state across runs that the contrast depends on being clean.
 */
public final class MongoStandalone {

    private static final DockerImageName IMAGE = DockerImageName.parse("mongo:8.0");

    private static final int MONGO_PORT = 27017;

    private static final GenericContainer<?> CONTAINER;

    static {
        CONTAINER = new GenericContainer<>(IMAGE)
                .withExposedPorts(MONGO_PORT)
                // Plain mongod. The absence of --replSet is the whole point of
                // this class; adding it would silently turn this into a second
                // replica set and the contrast test would pass for the wrong reason.
                .withCommand("mongod", "--bind_ip_all")
                .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        CONTAINER.start();
    }

    private MongoStandalone() {
    }

    public static String connectionString() {
        return "mongodb://%s:%d/test".formatted(CONTAINER.getHost(), CONTAINER.getMappedPort(MONGO_PORT));
    }
}
