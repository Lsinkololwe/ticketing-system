package com.pml.identity.revocation;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.revocation.support.ControllableTcpProxy;
import com.pml.identity.security.revocation.MongoRevocationStore;
import com.pml.identity.security.revocation.RevocationCacheWarmer;
import com.pml.identity.security.revocation.RevocationRepository;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationOnboardingService;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.web.graphql.mutation.OrganizationMutationResolver;
import com.pml.shared.security.revocation.CachedRevocationCheck;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.pml.shared.security.revocation.RevocationCacheTrust;
import com.pml.shared.security.revocation.RevocationCheck;
import com.pml.shared.security.revocation.RevocationGuardAspect;
import com.pml.shared.security.revocation.RevocationMetrics;
import com.pml.shared.security.revocation.RevocationProperties;
import com.pml.shared.security.revocation.RevocationType;
import com.pml.shared.security.revocation.RevocationUnavailableException;
import com.pml.shared.security.revocation.SensitiveOperationGuard;
import com.pml.shared.security.revocation.TokenRevokedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives the real {@link OrganizationMutationResolver} through the real guard, aspect and
 * revocation stores, with Redis and MongoDB reachable through cuttable proxies.
 *
 * <h2>What is being asserted</h2>
 * <p>That the organizer-application mutations are actually covered: a revoked token is refused,
 * an unresolvable one is refused, the resolver body does not run in either case, and the tier is
 * declared on every state-changing mutation rather than on whichever ones were remembered.</p>
 *
 * <h2>What is substituted, and why that is safe</h2>
 * <p>The three organization services are Mockito doubles. They are the thing being protected, not
 * the protection: the assertions are about whether the guard admits or refuses a call and whether
 * the body was reached, both of which a double reports faithfully. Everything on the revocation
 * path — the aspect, the guard, the cache, the durable store, the circuit breaker — is real.</p>
 *
 * <p>Method security is not enabled here, so {@code @PreAuthorize} does not participate. That
 * keeps a refusal unambiguous: it can only have come from the revocation tier.</p>
 */
@Tag("revocation")
@DisplayName("Organizer application mutations under revocation failure")
class OrganizerApplicationFailClosedIT {

    private static GenericContainer<?> redisContainer;
    private static GenericContainer<?> mongoContainer;
    private static ControllableTcpProxy redisProxy;
    private static ControllableTcpProxy mongoProxy;

    private static MongoClient mongoClient;
    private static LettuceConnectionFactory redisConnectionFactory;
    private static ReactiveStringRedisTemplate redis;
    private static RevocationRepository repository;

    private OrganizationService organizationService;
    private OrganizationOnboardingService onboardingService;
    private OrganizationMemberService memberService;

    private MongoRevocationStore durable;
    private RevocationCacheTrust cacheTrust;
    private RevocationCacheWarmer warmer;
    private OrganizationMutationResolver resolver;

    // =========================================================================
    // environment
    // =========================================================================

    @BeforeAll
    static void startEnvironment() {
        mongoContainer = new GenericContainer<>(DockerImageName.parse("mongo:8.0"))
                .withExposedPorts(27017)
                .waitingFor(Wait.forListeningPort());
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort());

        mongoContainer.start();
        redisContainer.start();

        mongoProxy = ControllableTcpProxy.forwardingTo(
                mongoContainer.getHost(), mongoContainer.getMappedPort(27017));
        redisProxy = ControllableTcpProxy.forwardingTo(
                redisContainer.getHost(), redisContainer.getMappedPort(6379));

        String mongoUri = "mongodb://%s:%d/organizer_guard_it?directConnection=true"
                .formatted(mongoProxy.getHost(), mongoProxy.getPort())
                + "&serverSelectionTimeoutMS=1500&connectTimeoutMS=1000&socketTimeoutMS=2000";

        mongoClient = MongoClients.create(mongoUri);
        repository = new ReactiveMongoRepositoryFactory(
                new ReactiveMongoTemplate(mongoClient, "organizer_guard_it"))
                .getRepository(RevocationRepository.class);

        redisConnectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redisProxy.getHost(), redisProxy.getPort()));
        redisConnectionFactory.setShareNativeConnection(false);
        redisConnectionFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisConnectionFactory);
    }

    @AfterAll
    static void stopEnvironment() {
        if (redisConnectionFactory != null) redisConnectionFactory.destroy();
        if (mongoClient != null) mongoClient.close();
        if (redisProxy != null) redisProxy.close();
        if (mongoProxy != null) mongoProxy.close();
        if (redisContainer != null) redisContainer.stop();
        if (mongoContainer != null) mongoContainer.stop();
    }

    @BeforeEach
    void buildGuardedResolver() {
        redisProxy.resume();
        mongoProxy.resume();

        RevocationProperties properties = new RevocationProperties();
        properties.setAccessTokenLifespan(Duration.ofHours(1));
        properties.setCacheTimeout(Duration.ofMillis(400));
        properties.setDurableTimeout(Duration.ofSeconds(3));

        RevocationMetrics metrics = new RevocationMetrics(new SimpleMeterRegistry());
        Clock clock = Clock.systemUTC();

        cacheTrust = new RevocationCacheTrust(redis, properties.getCacheCompletenessTtl());
        durable = new MongoRevocationStore(repository, redis, cacheTrust, properties, metrics, clock);
        warmer = new RevocationCacheWarmer(repository, redis, cacheTrust, properties, clock);

        CircuitBreaker breaker = CircuitBreaker.of("organizerGuardTest",
                CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(8)
                        .minimumNumberOfCalls(4)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .build());
        TimeLimiter timeLimiter = TimeLimiter.of("organizerGuardTest",
                TimeLimiterConfig.custom()
                        .timeoutDuration(properties.getCacheTimeout())
                        .cancelRunningFuture(true)
                        .build());

        RevocationCheck check = new CachedRevocationCheck(redis, cacheTrust, durable, properties,
                metrics, breaker, timeLimiter, clock);
        SensitiveOperationGuard guard =
                new SensitiveOperationGuard(check, properties, metrics);

        organizationService = mock(OrganizationService.class);
        onboardingService = mock(OrganizationOnboardingService.class);
        memberService = mock(OrganizationMemberService.class);

        // The aspect is applied exactly as Spring applies it in the running service.
        OrganizationMutationResolver target = new OrganizationMutationResolver(
                organizationService, onboardingService, memberService);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(new RevocationGuardAspect(guard));
        resolver = proxyFactory.getProxy();

        awaitStoresReachable();
        retrying(() -> repository.deleteAll().block(Duration.ofSeconds(10)));
        retrying(() -> redis.getConnectionFactory().getReactiveConnection().serverCommands()
                .flushDb().block(Duration.ofSeconds(10)));
        warmer.warm().block(Duration.ofSeconds(10));
    }

    // =========================================================================
    // the tier holds
    // =========================================================================

    @Test
    @DisplayName("an active token may submit an organizer application")
    void activeTokenMaySubmitAnApplication() {
        Organization organization = new Organization();
        when(onboardingService.applyToBeOrganizer(anyString(), any()))
                .thenReturn(Mono.just(organization));

        StepVerifier.create(asUser(newId(), newId(), newId(),
                        () -> resolver.applyToBeOrganizer(null)))
                .expectNext(organization)
                .verifyComplete();
    }

    @Test
    @DisplayName("a revoked token cannot submit an organizer application")
    void revokedTokenCannotSubmitAnApplication() {
        String jti = newId();
        durable.revoke(RevocationType.TOKEN, jti, "user_logout", "tester")
                .block(Duration.ofSeconds(10));

        StepVerifier.create(asUser(jti, newId(), newId(),
                        () -> resolver.applyToBeOrganizer(null)))
                .expectError(TokenRevokedException.class)
                .verify(Duration.ofSeconds(10));

        verify(onboardingService, never()).applyToBeOrganizer(anyString(), any());
    }

    @Test
    @DisplayName("a revoked session cannot submit an organizer application")
    void revokedSessionCannotSubmitAnApplication() {
        String sid = newId();
        durable.revoke(RevocationType.SESSION, sid, "backchannel_logout", "keycloak")
                .block(Duration.ofSeconds(10));

        StepVerifier.create(asUser(newId(), sid, newId(),
                        () -> resolver.applyToBeOrganizer(null)))
                .expectError(TokenRevokedException.class)
                .verify(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("a suspended user cannot have their application approved on their behalf")
    void suspendedUserIsRefusedOnAdminMutations() {
        String adminSub = newId();
        durable.revoke(RevocationType.USER, adminSub, "account_suspended", "platform")
                .block(Duration.ofSeconds(10));

        StepVerifier.create(asUser(newId(), newId(), adminSub,
                        () -> resolver.approveOrganization("org-1")))
                .expectError(TokenRevokedException.class)
                .verify(Duration.ofSeconds(10));

        verify(onboardingService, never()).approve(anyString(), anyString());
    }

    // =========================================================================
    // fail closed
    // =========================================================================

    @Test
    @DisplayName("with both stores unreachable the application is refused, not admitted")
    void unresolvableTokenIsRefused() {
        redisProxy.cut();
        mongoProxy.cut();

        StepVerifier.create(asUser(newId(), newId(), newId(),
                        () -> resolver.submitOrganizationForReview("org-1")))
                .expectError(RevocationUnavailableException.class)
                .verify(Duration.ofSeconds(20));

        verify(organizationService, never()).findById(anyString());
    }

    @Test
    @DisplayName("losing only the cache does not block the application")
    void cacheOutageAloneDoesNotBlock() {
        Organization organization = new Organization();
        when(onboardingService.applyToBeOrganizer(anyString(), any()))
                .thenReturn(Mono.just(organization));

        redisProxy.cut();

        StepVerifier.create(asUser(newId(), newId(), newId(),
                        () -> resolver.applyToBeOrganizer(null)))
                .as("one store is still answering, so refusing would be a self-inflicted outage")
                .expectNext(organization)
                .verifyComplete();
    }

    @Test
    @DisplayName("a token carrying no identifiers is refused on a guarded mutation")
    void unidentifiableTokenIsRefused() {
        StepVerifier.create(asUser(null, null, null,
                        () -> resolver.applyToBeOrganizer(null)))
                .expectError(RevocationUnavailableException.class)
                .verify(Duration.ofSeconds(10));
    }

    // =========================================================================
    // coverage of the tier itself
    // =========================================================================

    @Test
    @DisplayName("every state-changing organizer mutation declares the fail-closed tier")
    void everyMutationDeclaresTheTier() {
        List<String> unguarded = new ArrayList<>();

        for (Method method : OrganizationMutationResolver.class.getDeclaredMethods()) {
            boolean stateChanging = method.getAnnotations().length > 0
                    && java.util.Arrays.stream(method.getAnnotations())
                    .anyMatch(a -> a.annotationType().getSimpleName().equals("DgsMutation"));

            if (stateChanging && !method.isAnnotationPresent(FailClosedOnRevocation.class)) {
                unguarded.add(method.getName());
            }
        }

        assertThat(unguarded)
                .as("a mutation added without the annotation would silently opt out of the tier")
                .isEmpty();
    }

    @Test
    @DisplayName("the guard runs before the resolver body, not alongside it")
    void guardRunsBeforeTheBody() {
        String jti = newId();
        durable.revoke(RevocationType.TOKEN, jti, "admin_revoke", "tester")
                .block(Duration.ofSeconds(10));

        // A guard that assembled the target eagerly would invoke the service even on refusal.
        StepVerifier.create(asUser(jti, null, null,
                        () -> resolver.getOrCreateMyOrganization()))
                .expectError(TokenRevokedException.class)
                .verify(Duration.ofSeconds(10));

        verify(onboardingService, never()).getOrCreateOrganization(anyString());
    }

    // =========================================================================
    // helpers
    // =========================================================================

    /** Runs a resolver call inside a security context carrying the given claims. */
    private Mono<Organization> asUser(String jti, String sid, String sub,
                                      java.util.function.Supplier<Mono<Organization>> call) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofHours(1)));

        if (jti != null) builder.claim("jti", jti);
        if (sid != null) builder.claim("sid", sid);
        builder.subject(sub);

        Jwt jwt = builder.build();
        return Mono.defer(call)
                .contextWrite(ReactiveSecurityContextHolder
                        .withAuthentication(new JwtAuthenticationToken(jwt, List.of())));
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static void awaitStoresReachable() {
        retrying(() -> redis.hasKey("readiness-probe").block(Duration.ofSeconds(2)));
        retrying(() -> repository.count().block(Duration.ofSeconds(3)));
    }

    private static void retrying(Runnable action) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 25; attempt++) {
            try {
                action.run();
                return;
            } catch (RuntimeException e) {
                last = e;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Store did not become reachable again", last);
    }
}
