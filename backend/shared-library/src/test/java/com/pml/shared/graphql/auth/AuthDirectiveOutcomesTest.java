package com.pml.shared.graphql.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.GraphQlErrors;
import com.pml.shared.error.PlatformDataFetcherExceptionHandler;
import com.pml.shared.error.PlatformRefusalTranslator;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Instant;
import java.util.List;

/**
 * What a caller actually receives from an {@code @auth} field: the error code on the wire, and
 * proof that the data source was never reached when the answer was a refusal.
 *
 * <h2>The harness</h2>
 * The schema is wired exactly as the platform wires it — {@link AuthDirective} on the field, the
 * platform's own {@link PlatformDataFetcherExceptionHandler} with the platform's
 * {@link PlatformRefusalTranslator} deciding the error code — and executed through
 * {@code GraphQL.execute}. The only piece supplied by the test is the bridge from the
 * directive's {@code Mono} to a future inside a chosen Reactor context, which in a running
 * service is the GraphQL integration's job. The bridge is applied around the directive's
 * fetcher, so the directive itself is not altered.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("@auth fields answer with the right error code and never reach the repository when refusing")
class AuthDirectiveOutcomesTest {

    private interface Repository {
        String load();
    }

    private static final String SDL = """
            directive @auth(requires: Role = AUTHENTICATED) on FIELD_DEFINITION | OBJECT
            enum Role { PUBLIC AUTHENTICATED CUSTOMER ORGANIZER ADMIN SUPER_ADMIN FINANCE INTERNAL }
            type Query {
                adminField: String @auth(requires: ADMIN)
                internalField: String @auth(requires: INTERNAL)
            }
            """;

    private static Context signedIn(String... authorities) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("u-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        List<SimpleGrantedAuthority> granted = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new).toList();
        return ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt, granted));
    }

    /** A request with no authentication in its Reactor context at all. */
    private static Context anonymous() {
        return Context.empty();
    }

    private static ExecutionResult execute(Repository repository, String field, Context context) {
        GraphQLSchema guarded = new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(SDL),
                RuntimeWiring.newRuntimeWiring()
                        .directive("auth", new AuthDirective())
                        .type("Query", query -> query
                                .dataFetcher("adminField", env -> repository.load())
                                .dataFetcher("internalField", env -> repository.load()))
                        .build());

        // The directive hands back an unsubscribed Mono so the engine can subscribe it inside
        // the request's context; this bridge plays that part with the context under test.
        GraphQLCodeRegistry.Builder registry = GraphQLCodeRegistry.newCodeRegistry(guarded.getCodeRegistry());
        for (GraphQLFieldDefinition definition : guarded.getQueryType().getFieldDefinitions()) {
            FieldCoordinates coordinates = FieldCoordinates.coordinates("Query", definition.getName());
            DataFetcher<?> directiveFetcher = guarded.getCodeRegistry().getDataFetcher(coordinates, definition);
            DataFetcher<Object> bridged = env -> {
                Object result = directiveFetcher.get(env);
                return result instanceof Mono<?> mono ? mono.contextWrite(context).toFuture() : result;
            };
            registry.dataFetcher(coordinates, bridged);
        }
        GraphQLSchema wired = GraphQLSchema.newSchema(guarded).codeRegistry(registry.build()).build();

        GraphQL graphQl = GraphQL.newGraphQL(wired)
                .defaultDataFetcherExceptionHandler(new PlatformDataFetcherExceptionHandler(
                        List.of(new PlatformRefusalTranslator())))
                .build();
        return graphQl.execute("{ " + field + " }");
    }

    private static String errorCodeOf(ExecutionResult result) {
        assertThat(result.getErrors()).as("exactly one refusal is expected").hasSize(1);
        GraphQLError error = result.getErrors().get(0);
        return String.valueOf(error.getExtensions().get(GraphQlErrors.ERROR_CODE));
    }

    @Test
    @DisplayName("no authentication: ACTOR_NOT_AUTHENTICATED, and the repository is never touched")
    void noAuthenticationIsNotAuthenticated() {
        Repository repository = mock(Repository.class);

        ExecutionResult result = execute(repository, "adminField", anonymous());

        assertThat(errorCodeOf(result))
                .as("a caller with no identity is unauthenticated, not merely unpermitted")
                .isEqualTo(ErrorCode.ACTOR_NOT_AUTHENTICATED.name());
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("a CUSTOMER on an ADMIN field: ACTOR_NOT_PERMITTED, and the repository is never touched")
    void customerOnAdminFieldIsNotPermitted() {
        Repository repository = mock(Repository.class);

        ExecutionResult result = execute(repository, "adminField", signedIn("ROLE_CUSTOMER"));

        assertThat(errorCodeOf(result)).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED.name());
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("an ADMIN on an ADMIN field is answered from the repository")
    void adminOnAdminFieldIsAnswered() {
        Repository repository = mock(Repository.class);
        when(repository.load()).thenReturn("the actual row");

        ExecutionResult result = execute(repository, "adminField", signedIn("ROLE_ADMIN"));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.<java.util.Map<String, Object>>getData()).containsEntry("adminField", "the actual row");
        verify(repository).load();
    }

    @Test
    @DisplayName("INTERNAL: an ADMIN with no internal scope is refused and the repository is never touched")
    void adminWithoutInternalScopeIsRefusedOnInternalField() {
        Repository repository = mock(Repository.class);

        ExecutionResult result = execute(repository, "internalField", signedIn("ROLE_ADMIN"));

        assertThat(errorCodeOf(result)).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED.name());
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("INTERNAL: a token with the internal-read scope is allowed")
    void internalReadScopeIsAllowed() {
        Repository repository = mock(Repository.class);
        when(repository.load()).thenReturn("internal row");

        ExecutionResult result = execute(repository, "internalField", signedIn("SCOPE_internal-read"));

        assertThat(result.getErrors()).isEmpty();
        verify(repository).load();
    }

    @Test
    @DisplayName("INTERNAL: ROLE_INTERNAL_SERVICE is allowed")
    void internalServiceRoleIsAllowed() {
        Repository repository = mock(Repository.class);
        when(repository.load()).thenReturn("internal row");

        ExecutionResult result = execute(repository, "internalField", signedIn("ROLE_INTERNAL_SERVICE"));

        assertThat(result.getErrors()).isEmpty();
        verify(repository).load();
    }
}
