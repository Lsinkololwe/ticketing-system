package com.pml.identity.platform;

import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.domain.enums.AlertSeverity;
import com.pml.identity.platform.HealthRules.Status;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Asks every dependency whether it is up, and keeps the alert list honest about the answer: a
 * dependency that is down raises (or re-counts) one alert, and one that has recovered closes it.
 *
 * <p>The probes are what identity itself needs (MongoDB, Redis, Keycloak) plus the downstream
 * services named in {@code identity.health.targets}. A probe that fails reports a category such
 * as "timeout"; no address, credential or stack trace leaves this class.
 */
@Slf4j
@Service
public class ServiceHealthService {

    static final String SOURCE = "health-probe";

    private final ReactiveMongoTemplate mongo;
    private final ObjectProvider<ReactiveRedisConnectionFactory> redis;
    private final KeycloakProperties keycloak;
    private final HealthProbeProperties properties;
    private final PlatformOpsService ops;
    private final WebClient.Builder webClients;
    private final Clock clock;

    public ServiceHealthService(ReactiveMongoTemplate mongo, ObjectProvider<ReactiveRedisConnectionFactory> redis,
                                KeycloakProperties keycloak, HealthProbeProperties properties,
                                PlatformOpsService ops, WebClient.Builder webClients, Clock clock) {
        this.mongo = mongo;
        this.redis = redis;
        this.keycloak = keycloak;
        this.properties = properties;
        this.ops = ops;
        this.webClients = webClients;
        this.clock = clock;
    }

    public Flux<ServiceHealth> check() {
        List<Mono<ServiceHealth>> probes = new ArrayList<>();
        probes.add(probe("mongodb", mongo.executeCommand(new Document("ping", 1)).then()));
        ReactiveRedisConnectionFactory factory = redis.getIfAvailable();
        if (factory != null) {
            probes.add(probe("redis", Mono.usingWhen(Mono.fromSupplier(factory::getReactiveConnection),
                    connection -> connection.ping().then(), connection -> Mono.fromRunnable(connection::close))));
        }
        probes.add(http("keycloak", keycloak.getServerUrl() + "/realms/" + keycloak.getRealm()
                + "/.well-known/openid-configuration", false));
        properties.getTargets().forEach((name, baseUrl) -> probes.add(http(name, baseUrl + "/actuator/health", true)));
        return Flux.merge(probes)
                .concatMap(result -> reconcile(result).thenReturn(result))
                .sort(java.util.Comparator.comparing(ServiceHealth::name));
    }

    private Mono<ServiceHealth> http(String name, String url, boolean actuator) {
        long start = System.nanoTime();
        return webClients.build().get().uri(url).exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> actuator
                                ? HealthRules.ofActuator(response.statusCode().value(), body)
                                : (response.statusCode().is2xxSuccessful() ? Status.UP : Status.DOWN)))
                .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .map(status -> result(name, status, start, status == Status.UP ? null : "unhealthy"))
                .onErrorResume(failure -> Mono.just(result(name, Status.DOWN, start, HealthRules.category(failure))));
    }

    private Mono<ServiceHealth> probe(String name, Mono<Void> check) {
        long start = System.nanoTime();
        return check.timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .then(Mono.fromSupplier(() -> result(name, Status.UP, start, null)))
                .onErrorResume(failure -> Mono.just(result(name, Status.DOWN, start, HealthRules.category(failure))));
    }

    private ServiceHealth result(String name, Status status, long startNanos, String detail) {
        return new ServiceHealth(name, status, (System.nanoTime() - startNanos) / 1_000_000, clock.instant(), detail);
    }

    private Mono<Void> reconcile(ServiceHealth health) {
        String key = HealthRules.alertKey(health.name());
        Mono<Void> outcome = health.status() == Status.DOWN
                ? ops.raise(SOURCE, key, AlertSeverity.CRITICAL, health.name() + " is down",
                        "The health probe could not reach " + health.name() + " (" + health.detail() + ").").then()
                : ops.resolve(SOURCE, key);
        // An alert store that is itself unavailable must not hide the health answer it was asked for.
        return outcome.onErrorResume(error -> {
            log.warn("Could not reconcile the alert for {}: {}", health.name(), error.toString());
            return Mono.empty();
        });
    }
}
