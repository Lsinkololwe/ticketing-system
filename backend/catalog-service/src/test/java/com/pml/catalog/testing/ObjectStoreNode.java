package com.pml.catalog.testing;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A real S3-compatible object store (LocalStack) for the tests that presign and put objects. One
 * container for the whole test run; each test class takes its own bucket.
 */
public final class ObjectStoreNode {

    public static final String ACCESS_KEY = "test";
    public static final String SECRET_KEY = "test";
    private static final GenericContainer<?> NODE = new GenericContainer<>(DockerImageName.parse("localstack/localstack:3.8"))
            .withEnv("SERVICES", "s3")
            .withExposedPorts(4566)
            .waitingFor(Wait.forHttp("/_localstack/health").forPort(4566).forStatusCode(200));

    static {
        NODE.start();
    }

    private ObjectStoreNode() {
    }

    /** The address clients use, for example {@code http://127.0.0.1:49153}. */
    public static String endpoint() {
        return "http://" + NODE.getHost() + ":" + NODE.getMappedPort(4566);
    }
}
