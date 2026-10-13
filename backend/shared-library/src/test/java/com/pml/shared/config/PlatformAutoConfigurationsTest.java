package com.pml.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.shared.config.PlatformAuditingAutoConfiguration;
import com.pml.shared.config.PlatformClockAutoConfiguration;
import com.pml.shared.config.PlatformJacksonAutoConfiguration;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.infrastructure.temporal.TemporalGatewayAutoConfiguration;
import com.pml.shared.persistence.PlatformMongoTransactionAutoConfiguration;
import com.pml.shared.security.ServiceSecurity;
import com.pml.shared.security.ServiceSecurityAutoConfiguration;
import com.pml.shared.security.tenancy.TenancyProperties;
import com.pml.shared.security.tenancy.TenantMemberships;
import com.pml.shared.security.tenancy.TenantScopeAutoConfiguration;
import com.pml.shared.security.tenancy.TenantScopeWebFilter;
import io.temporal.client.WorkflowClient;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.springframework.web.reactive.function.client.WebClient;
import com.pml.shared.security.revocation.RemoteRevocationStoreAutoConfiguration;
import com.pml.shared.security.revocation.HttpDurableRevocationStore;
import com.pml.shared.security.revocation.DurableRevocationStore;
import com.pml.shared.security.InternalServiceWebClients;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * What the platform auto-configurations contribute to every service, each group against the real
 * conditions with {@link ApplicationContextRunner} — no container and no running service. These
 * replaced a copy of the same configuration in each of three services, so they are asserted once.
 */
@Tag("L3")
@Tag("ET-PLT-001")
@DisplayName("The platform auto-configurations every service relies on")
class PlatformAutoConfigurationsTest {

    // ── Every service's JSON follows one set of conventions, on Boot's own mapper ─────────────────


    private final ApplicationContextRunner jsonConventionsRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, PlatformJacksonAutoConfiguration.class));

    record Sample(Instant at, String note) {
    }

    @Test
    @DisplayName("Json conventions · an instant is written as ISO-8601, not as a number")
    void jsonConventions_instantsAreIsoStrings() {
        jsonConventionsRunner.run(context -> assertThat(context.getBean(ObjectMapper.class)
                .writeValueAsString(new Sample(Instant.parse("2026-09-19T10:15:30Z"), "x")))
                .contains("\"at\":\"2026-09-19T10:15:30Z\""));
    }

    @Test
    @DisplayName("Json conventions · an unknown property is ignored, so a caller can evolve ahead of the service")
    void jsonConventions_unknownPropertiesAreIgnored() {
        jsonConventionsRunner.run(context -> assertThat(context.getBean(ObjectMapper.class)
                .readValue("{\"note\":\"n\",\"addedLater\":1}", Sample.class).note())
                .isEqualTo("n"));
    }

    @Test
    @DisplayName("Json conventions · a null is written, because GraphQL clients tell an absent field from a null one")
    void jsonConventions_nullsAreWritten() {
        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("note", null);
        jsonConventionsRunner.run(context -> assertThat(context.getBean(ObjectMapper.class).writeValueAsString(withNull))
                .isEqualTo("{\"note\":null}"));
    }

    @Test
    @DisplayName("Json conventions · Boot's mapper is customised, not replaced — there is still exactly one")
    void jsonConventions_bootsMapperIsKept() {
        jsonConventionsRunner.run(context -> assertThat(context).hasSingleBean(ObjectMapper.class));
    }

    // ── Every service gets one Mongo transaction manager and its operator ─────────────────────────


    private final ApplicationContextRunner mongoTransactionPairRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformMongoTransactionAutoConfiguration.class));

    @Test
    @DisplayName("Mongo transaction pair · with a database factory: a Mongo transaction manager and a TransactionalOperator on it")
    void mongoTransactionPair_contributesTheTransactionPair() {
        mongoTransactionPairRunner.withBean(ReactiveMongoDatabaseFactory.class, () -> mock(ReactiveMongoDatabaseFactory.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ReactiveTransactionManager.class);
                    assertThat(context.getBean(ReactiveTransactionManager.class))
                            .isInstanceOf(ReactiveMongoTransactionManager.class);
                    assertThat(context).hasSingleBean(TransactionalOperator.class);
                    assertThat(context).hasBean("reactiveTransactionManager");
                });
    }

    @Test
    @DisplayName("Mongo transaction pair · without Mongo — the gateway — nothing")
    void mongoTransactionPair_backsOffWithoutMongo() {
        mongoTransactionPairRunner.run(context -> assertThat(context).doesNotHaveBean(ReactiveTransactionManager.class));
    }

    // ── Audit fields name the Keycloak user who wrote the document ────────────────────────────────


    private final ApplicationContextRunner auditFieldsRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PlatformClockAutoConfiguration.class, PlatformAuditingAutoConfiguration.class))
            .withBean("mongoMappingContext", MongoMappingContext.class, MongoMappingContext::new);

    private static Jwt jwt(String subject, String username) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none")
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(60));
        if (subject != null) {
            builder.subject(subject);
        }
        if (username != null) {
            builder.claim("preferred_username", username);
        }
        return builder.build();
    }

    @Test
    @DisplayName("Audit fields name the Keycloak user who wrote the document · the auditor is the JWT subject")
    void auditFields_subjectIsTheAuditor() {
        auditFieldsRunner.run(context -> {
            @SuppressWarnings("unchecked")
            ReactiveAuditorAware<String> auditor = context.getBean(ReactiveAuditorAware.class);
            var authentication = new JwtAuthenticationToken(jwt("user-42", "chanda"), java.util.List.of());
            assertThat(auditor.getCurrentAuditor()
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                    .block()).isEqualTo("user-42");
        });
    }

    @Test
    @DisplayName("Audit fields name the Keycloak user who wrote the document · work no user started is written by 'system'")
    void auditFields_noUserIsSystem() {
        auditFieldsRunner.run(context -> {
            @SuppressWarnings("unchecked")
            ReactiveAuditorAware<String> auditor = context.getBean(ReactiveAuditorAware.class);
            assertThat(auditor.getCurrentAuditor().block()).isEqualTo("system");
        });
    }

    @Test
    @DisplayName("Audit fields name the Keycloak user who wrote the document · without Spring Data Mongo on the classpath — the gateway — no auditing, only the clock")
    void auditFields_noMongoNoAuditing() {
        new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader(ReactiveMongoTemplate.class))
                .withConfiguration(AutoConfigurations.of(
                        PlatformClockAutoConfiguration.class, PlatformAuditingAutoConfiguration.class))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ReactiveAuditorAware.class));
    }


    // ── The tenant scope is installed wherever a service can resolve memberships ──────────────────


    private final ApplicationContextRunner tenantScopeFilterRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantScopeAutoConfiguration.class));

    private static final TenantMemberships MEMBERSHIPS = subject -> Mono.just(Set.of("org-1"));

    @Test
    @DisplayName("Tenant scope filter · a service with memberships gets the filter, administrators platform-wide by default")
    void tenantScopeFilter_installsTheFilter() {
        tenantScopeFilterRunner.withBean(TenantMemberships.class, () -> MEMBERSHIPS).run(context -> {
            assertThat(context).hasSingleBean(TenantScopeWebFilter.class);
            assertThat(context.getBean(TenancyProperties.class).platformWideAuthorities())
                    .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_SUPER_ADMIN");
        });
    }

    @Test
    @DisplayName("Tenant scope filter · a service widens who is platform-wide in configuration, not in code")
    void tenantScopeFilter_platformWideAuthoritiesAreConfigured() {
        tenantScopeFilterRunner.withBean(TenantMemberships.class, () -> MEMBERSHIPS)
                .withPropertyValues("platform.tenancy.platform-wide-authorities=ROLE_ADMIN,ROLE_FINANCE")
                .run(context -> assertThat(context.getBean(TenancyProperties.class).platformWideAuthorities())
                        .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_FINANCE"));
    }

    @Test
    @DisplayName("Tenant scope filter · without a membership source there is no filter — and tenant reads fail loudly")
    void tenantScopeFilter_noMembershipsNoFilter() {
        tenantScopeFilterRunner.run(context -> assertThat(context).doesNotHaveBean(TenantScopeWebFilter.class));
    }

    // ── One TemporalGateway wherever the starter built a client ───────────────────────────────────


    private final ApplicationContextRunner temporalClientGatewayRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TemporalGatewayAutoConfiguration.class));

    @Test
    @DisplayName("One TemporalGateway wherever the starter built a client · with a WorkflowClient: the gateway, holding that client")
    void temporalClientGateway_wrapsTheClient() {
        WorkflowClient client = mock(WorkflowClient.class);
        temporalClientGatewayRunner.withBean(WorkflowClient.class, () -> client).run(context -> {
            assertThat(context).hasSingleBean(TemporalGateway.class);
            assertThat(context.getBean(TemporalGateway.class).client()).isSameAs(client);
        });
    }

    @Test
    @DisplayName("One TemporalGateway wherever the starter built a client · without one — a service that runs no worker — nothing")
    void temporalClientGateway_backsOffWithoutAClient() {
        temporalClientGatewayRunner.run(context -> assertThat(context).doesNotHaveBean(TemporalGateway.class));
    }

    @Test
    @DisplayName("One TemporalGateway wherever the starter built a client · a blocking client call runs off the calling thread")
    void temporalClientGateway_blockingCallsLeaveTheCaller() {
        TemporalGateway gateway = new TemporalGateway(mock(WorkflowClient.class));
        String caller = Thread.currentThread().getName();
        assertThat(gateway.call(() -> Thread.currentThread().getName()).block()).isNotEqualTo(caller);
    }

    // ── Every domain service runs the same resource-server chain unless it declares its own ───────


    // "test" exempts this context from the audience-required check (ET-PLT-007 R2): it is
    // one, and carries no oidc-audience-mapper the way a real deployment's realm would.
    private final ReactiveWebApplicationContextRunner serviceSecurityChainRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ServiceSecurityAutoConfiguration.class))
            .withPropertyValues(
                    "spring.profiles.active=test",
                    "spring.application.name=booking-service",
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8084/realms/myticketzm");

    @Test
    @DisplayName("Service security chain · installs one chain and a decoder that fetches nothing at startup")
    void serviceSecurityChain_installsTheChain() {
        serviceSecurityChainRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SecurityWebFilterChain.class);
            assertThat(context).hasSingleBean(ReactiveJwtDecoder.class);
            assertThat(context.getBean(ServiceSecurity.class).clientId()).isEqualTo("booking-service");
        });
    }

    @Test
    @DisplayName("Service security chain · a service's public paths come from configuration")
    void serviceSecurityChain_publicPathsAreConfigured() {
        serviceSecurityChainRunner.withPropertyValues("platform.security.public-paths=/api/webhooks/payment/**")
                .run(context -> assertThat(context.getBean(ServiceSecurity.class).publicPaths())
                        .containsExactly("/api/webhooks/payment/**"));
    }

    @Test
    @DisplayName("Service security chain · public paths written as a YAML list — indexed keys — are bound too")
    void serviceSecurityChain_publicPathsAsAYamlList() {
        serviceSecurityChainRunner.withPropertyValues(
                        "platform.security.public-paths[0]=/api/webhooks/payment/**",
                        "platform.security.public-paths[1]=/api/other/**")
                .run(context -> assertThat(context.getBean(ServiceSecurity.class).publicPaths())
                        .containsExactly("/api/webhooks/payment/**", "/api/other/**"));
    }

    @Test
    @DisplayName("Service security chain · a service with a chain of its own keeps it — identity, the gateway")
    void serviceSecurityChain_stepsAsideForAServicesOwnChain() {
        serviceSecurityChainRunner.withUserConfiguration(OwnChain.class).run(context -> {
            assertThat(context).hasSingleBean(SecurityWebFilterChain.class);
            assertThat(context).hasBean("ownChain");
        });
    }

    @Test
    @DisplayName("Service security chain · no issuer configured, no chain — the service fails closed on Boot's defaults instead")
    void serviceSecurityChain_noIssuerNoChain() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ServiceSecurityAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(ServiceSecurity.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebFluxSecurity
    static class OwnChain {
        @Bean
        SecurityWebFilterChain ownChain(ServerHttpSecurity http) {
            return http.authorizeExchange(e -> e.anyExchange().denyAll()).build();
        }
    }

    // ── A service without revocation records reads identity's over its internal API ───────────────

    private final ApplicationContextRunner remoteRevocationStoreRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RemoteRevocationStoreAutoConfiguration.class))
            .withBean(InternalServiceWebClients.class,
                    () -> InternalServiceWebClients.unauthenticated(WebClient.builder()))
            .withPropertyValues("pml.security.revocation.enabled=true",
                    "pml.security.revocation.identity-url=http://identity:8083");

    @Test
    @DisplayName("Remote revocation store · with identity named: an HTTP store")
    void remoteRevocationStore_httpStore() {
        remoteRevocationStoreRunner.run(context -> assertThat(context).hasSingleBean(HttpDurableRevocationStore.class));
    }

    @Test
    @DisplayName("Remote revocation store · identity itself, which owns a store, keeps its own")
    void remoteRevocationStore_ownerKeepsItsStore() {
        DurableRevocationStore own = mock(DurableRevocationStore.class);
        remoteRevocationStoreRunner.withBean(DurableRevocationStore.class, () -> own)
                .run(context -> assertThat(context).doesNotHaveBean(HttpDurableRevocationStore.class));
    }

    @Test
    @DisplayName("Remote revocation store · revocation switched off: nothing")
    void remoteRevocationStore_disabled() {
        remoteRevocationStoreRunner.withPropertyValues("pml.security.revocation.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(HttpDurableRevocationStore.class));
    }
}
