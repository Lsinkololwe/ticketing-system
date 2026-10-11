package com.pml.shared.graphql.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Instant;
import java.util.List;

/**
 * ET-PLT-007-R3 · Both mechanisms {@code OperationGateLintTest} treats as an equally valid access
 * decision — {@code @auth} in the schema and {@code @PreAuthorize} on the resolver — are proven
 * here to deny BEFORE the underlying data fetch happens, not just to answer the caller with a
 * refusal. A directive that denied correctly but still called the repository first would leak
 * whatever side effect or timing the fetch has; {@code AuthDirective}'s own javadoc documents
 * deny-before-fetch as the intent, but until now nothing asserted it — only HTTP-response-shaped
 * tests existed.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("@auth and @PreAuthorize both deny before the repository is ever called")
class AuthDirectiveRepositoryGuardTest {

    // ---- @auth: a minimal schema, the directive wired exactly as AuthDirectiveAutoConfiguration does ----

    private interface Repository {
        String load();
    }

    private static Context signedIn(String... roles) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("u-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        List<SimpleGrantedAuthority> authorities = List.of(roles).stream().map(SimpleGrantedAuthority::new).toList();
        return ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }

    private DataFetcher<?> authGuardedFetcher(Repository repository) {
        String sdl = """
                directive @auth(requires: Role = AUTHENTICATED) on FIELD_DEFINITION | OBJECT
                enum Role { PUBLIC AUTHENTICATED CUSTOMER ORGANIZER ADMIN SUPER_ADMIN FINANCE INTERNAL }
                type Query { adminField: String @auth(requires: ADMIN) }
                """;
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .directive("auth", new AuthDirective())
                .type("Query", builder -> builder.dataFetcher("adminField", env -> repository.load()))
                .build();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        GraphQLFieldDefinition field = schema.getQueryType().getFieldDefinition("adminField");
        return schema.getCodeRegistry().getDataFetcher(FieldCoordinates.coordinates("Query", "adminField"), field);
    }

    @Test
    @DisplayName("@auth(requires: ADMIN): a CUSTOMER is refused and the repository is never called")
    void authDeniesBeforeFetch() throws Exception {
        Repository repository = mock(Repository.class);
        DataFetcher<?> fetcher = authGuardedFetcher(repository);

        Object result = fetcher.get(DataFetchingEnvironmentImpl.newDataFetchingEnvironment().build());
        assertThat(result).isInstanceOf(Mono.class);

        assertThatThrownBy(() -> ((Mono<?>) result).contextWrite(signedIn("ROLE_CUSTOMER")).block())
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("@auth(requires: ADMIN): an ADMIN reaches the repository and gets its answer")
    void authAllowsAndFetches() throws Exception {
        Repository repository = mock(Repository.class);
        when(repository.load()).thenReturn("the actual row");
        DataFetcher<?> fetcher = authGuardedFetcher(repository);

        Object result = fetcher.get(DataFetchingEnvironmentImpl.newDataFetchingEnvironment().build());
        Object answer = ((Mono<?>) result).contextWrite(signedIn("ROLE_ADMIN")).block();

        assertThat(answer).isEqualTo("the actual row");
        verify(repository).load();
    }

    // ---- @PreAuthorize: the resolver runs behind Spring Security's own reactive method-security proxy ----

    public static class Resolver {

        private final Repository repository;

        Resolver(Repository repository) {
            this.repository = repository;
        }

        @PreAuthorize("hasRole('ADMIN')")
        public Mono<String> adminField() {
            return Mono.just(repository.load());
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableReactiveMethodSecurity
    static class Config {

        private final Repository repository;

        Config(Repository repository) {
            this.repository = repository;
        }

        @Bean
        Resolver resolver() {
            return new Resolver(repository);
        }
    }

    private Resolver preAuthorizeGuardedResolver(Repository repository) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(Repository.class, () -> repository);
        context.register(Config.class);
        context.refresh();
        return context.getBean(Resolver.class);
    }

    @Test
    @DisplayName("@PreAuthorize(\"hasRole('ADMIN')\"): a CUSTOMER is refused and the repository is never called")
    void preAuthorizeDeniesBeforeFetch() {
        Repository repository = mock(Repository.class);
        Resolver resolver = preAuthorizeGuardedResolver(repository);

        assertThatThrownBy(() -> resolver.adminField().contextWrite(signedIn("ROLE_CUSTOMER")).block())
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("@PreAuthorize(\"hasRole('ADMIN')\"): an ADMIN reaches the repository and gets its answer")
    void preAuthorizeAllowsAndFetches() {
        Repository repository = mock(Repository.class);
        when(repository.load()).thenReturn("the actual row");
        Resolver resolver = preAuthorizeGuardedResolver(repository);

        String answer = resolver.adminField().contextWrite(signedIn("ROLE_ADMIN")).block();

        assertThat(answer).isEqualTo("the actual row");
        verify(repository).load();
    }
}
