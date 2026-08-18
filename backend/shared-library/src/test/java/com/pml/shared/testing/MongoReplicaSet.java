package com.pml.shared.testing;

import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The platform's MongoDB test container — a real single-node <em>replica set</em>.
 *
 * <h2>Why a replica set and not a plain {@code mongod}</h2>
 * ET-PLT-002 D-01: against a standalone {@code mongod} a reactive
 * {@code @Transactional} method is <strong>silently inert</strong>. It does not
 * error, it does nothing. Reservations, escrow movements and journal pairs are
 * all multi-document writes, so a standalone development database gives you a
 * platform that oversells under load and passes every one of its tests.
 *
 * <p>{@link MongoDBContainer} starts {@code mongod --replSet} and issues
 * {@code rs.initiate()} for us, so transactions are genuinely available here.
 * {@link MongoStandalone} exists purely so the contrast can be asserted rather
 * than asserted-about — see {@code TransactionRealityTest}.
 *
 * <h2>Singleton, not per-class</h2>
 * One container for the whole suite. A container per test class turns a
 * ten-minute suite into an hour, and a suite that slow is a suite people switch
 * off — at which point it has stopped being a gate.
 *
 * <p>Started once in a static initialiser; Testcontainers' Ryuk sidecar reaps it
 * when the JVM exits, so there is no {@code stop()} to forget.
 */
public final class MongoReplicaSet {

    /** Pinned to the 8.x line CONVENTIONS.md §0 fixes for business data. */
    private static final DockerImageName IMAGE = DockerImageName.parse("mongo:8.0");

    private static final MongoDBContainer CONTAINER;

    static {
        CONTAINER = new MongoDBContainer(IMAGE)
                // Honours `testcontainers.reuse.enable` when the developer has
                // opted in; a no-op in CI, where the daemon is fresh anyway.
                .withReuse(true);
        CONTAINER.start();
    }

    private MongoReplicaSet() {
    }

    /** {@code mongodb://host:port/test}, with the replica set already initiated. */
    public static String connectionString() {
        return CONTAINER.getReplicaSetUrl();
    }

    public static MongoDBContainer container() {
        return CONTAINER;
    }
}
