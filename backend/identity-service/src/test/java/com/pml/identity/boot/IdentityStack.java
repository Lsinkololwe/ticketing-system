package com.pml.identity.boot;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TemporalDevServer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.util.TestSocketUtils;

import java.util.UUID;

/**
 * What the full-context identity tests share: where the infrastructure containers are, expressed as
 * Spring properties. Profile {@code test} supplies the development secrets
 * ({@code src/test/resources/application-test.yml}); everything pointing at a container is here.
 */
final class IdentityStack {

    private IdentityStack() {
    }

    static String newDatabase() {
        return "identity_boot_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    static MongoClient mongoClient() {
        return MongoClients.create(MongoReplicaSet.connectionString());
    }

    /** Mongo replica set, Redis, Temporal, and the settings with no safe default that a context needs to start. */
    static void register(DynamicPropertyRegistry registry, String database) {
        registry.add("spring.data.mongodb.uri", MongoReplicaSet::connectionString);
        registry.add("spring.data.mongodb.database", () -> database);
        registry.add("spring.data.redis.host", RedisNode::host);
        registry.add("spring.data.redis.port", RedisNode::port);
        registry.add("spring.temporal.connection.target", TemporalDevServer::target);
        registry.add("spring.temporal.namespace", () -> TemporalDevServer.NAMESPACE);
        // placeholders application.yml leaves without a default
        registry.add("IDENTITY_SERVICE_SECRET", () -> "test-identity-service-secret");
        registry.add("KEYCLOAK_ADMIN_PASSWORD", () -> "test-admin-password");
        // no Service Bus here: the test binder stands in for the wire only
        registry.add("spring.cloud.stream.default-binder", () -> "test");
        registry.add("server.port", () -> TestSocketUtils.findAvailableTcpPort());
    }
}
