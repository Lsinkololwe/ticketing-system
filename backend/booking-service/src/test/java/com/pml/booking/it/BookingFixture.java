package com.pml.booking.it;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.infrastructure.ratelimit.ActionRateLimiter;
import com.pml.booking.security.OrganizerAccess;
import com.pml.shared.dto.EventSummaryDto;
import com.pml.shared.dto.authorization.AuthorizationResult;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Real MongoDB (replica set), real Redis and a caller's token, for the booking integration tests.
 *
 * <p>Money is stored as Decimal128, as the running service does, so aggregations over it behave as in
 * production. The two neighbouring services are mocked at their clients; everything booking owns is real.
 */
public final class BookingFixture {

    private BookingFixture() {
    }

    /** A client for the replica set; the caller names it ({@code MongoReplicaSet.connectionString()}) so a test's layer is visible in the test. */
    public static MongoClient newClient(String connectionString) {
        return MongoClients.create(connectionString);
    }

    public static ReactiveMongoTemplate template(MongoClient client, String prefix) {
        var factory = new SimpleReactiveMongoDatabaseFactory(client, prefix + "_" + UUID.randomUUID().toString().substring(0, 8));
        var conversions = MongoCustomConversions.create(a -> a.bigDecimal(MongoCustomConversions.BigDecimalRepresentation.DECIMAL128));
        var context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        return new ReactiveMongoTemplate(factory, converter);
    }

    /** The indexes production creates at boot, so a test meets the same uniqueness the running service has. */
    public static void productionIndexes(ReactiveMongoTemplate template) {
        new com.pml.shared.persistence.IndexEnsurer(template)
                .ensure(com.pml.booking.config.BookingIndexInitializer.specifications()).block();
    }

    /**
     * Wraps a service in the transaction advice Spring puts around its {@code @Transactional} methods in the
     * running application, so the test sees the isolation production has and not the bare methods.
     */
    @SuppressWarnings("unchecked")
    public static <T> T transactional(Class<T> type, T target, ReactiveMongoTemplate template) {
        var interceptor = new org.springframework.transaction.interceptor.TransactionInterceptor(
                new org.springframework.data.mongodb.ReactiveMongoTransactionManager(template.getMongoDatabaseFactory()),
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
        var factory = new org.springframework.aop.framework.ProxyFactory(target);
        factory.setInterfaces(type);
        factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }

    private static volatile ReactiveStringRedisTemplate redis;

    public static synchronized ReactiveStringRedisTemplate redis() {
        if (redis == null) {
            var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(RedisNode.host(), RedisNode.port()));
            factory.afterPropertiesSet();
            redis = new ReactiveStringRedisTemplate(factory);
        }
        return redis;
    }

    public static ActionRateLimiter limiter() {
        return new ActionRateLimiter(redis());
    }

    /** A catalog that knows these events, and an identity that grants {@code permitted} users on {@code eventId}. */
    public static final class World {
        public final CatalogServiceClient catalog = Mockito.mock(CatalogServiceClient.class);
        public final IdentityServiceClient identity = Mockito.mock(IdentityServiceClient.class);
        public final OrganizerAccess access = new OrganizerAccess(catalog, identity);

        private final java.util.Set<String> grants = java.util.concurrent.ConcurrentHashMap.newKeySet();

        public World() {
            when(catalog.getEventById(any())).thenReturn(Mono.empty());
            when(identity.checkAuthorization(any())).thenAnswer(call -> {
                var request = (com.pml.shared.dto.authorization.AuthorizationRequest) call.getArgument(0);
                boolean allowed = grants.contains(request.getUserId() + "|" + request.getEventId())
                        || grants.contains(request.getUserId() + "|org:" + request.getOrganizationId());
                return Mono.just(allowed
                        ? AuthorizationResult.authorized("test", "ORGANIZATION_MEMBER", "ORGANIZER")
                        : AuthorizationResult.builder().authorized(false).build());
            });
        }

        public EventSummaryDto event(String id, String organizationId) {
            EventSummaryDto event = new EventSummaryDto();
            event.setId(id);
            event.setTitle("Event " + id);
            event.setOrganizationId(organizationId);
            when(catalog.getEventById(id)).thenReturn(Mono.just(event));
            return event;
        }

        /** {@code userId} holds every permission on the event (as a member of its organization would). */
        public void grant(String userId, String eventId) {
            grants.add(userId + "|" + eventId);
        }

        /** {@code userId} holds every permission across the organization. */
        public void grantOrganization(String userId, String organizationId) {
            grants.add(userId + "|org:" + organizationId);
        }
    }

    /** Runs {@code call} as a signed-in user with these authorities and tenant scope. */
    public static <T> Mono<T> as(String userId, TenantScope scope, Mono<T> call, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(userId).build();
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
        return call
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt, authorities)));
    }

    public static <T> Mono<T> asCustomer(String userId, Mono<T> call) {
        return as(userId, TenantScope.of(userId, Set.of()), call, "ROLE_CUSTOMER");
    }

    public static <T> Mono<T> asOrganizer(String userId, String organizationId, Mono<T> call) {
        return as(userId, TenantScope.of(userId, Set.of(organizationId)), call, "ROLE_ORGANIZER");
    }

    public static <T> Mono<T> asAdmin(String userId, Mono<T> call, String role) {
        return as(userId, TenantScope.platformAdministrator(userId, Set.of()), call, role);
    }

    /** The refusal a call ends in; fails the test if it succeeds or fails otherwise. */
    public static DomainRefusal refusal(Mono<?> call) {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        assertThatThrownBy(call::block).satisfies(thrown::set);
        assertThat(thrown.get()).isInstanceOf(DomainRefusal.class);
        return (DomainRefusal) thrown.get();
    }

    public static void assertRefusedWith(Mono<?> call, ErrorCode code) {
        assertThat(refusal(call).errorCode()).isEqualTo(code);
    }
}
