package com.pml.identity.web.graphql;

import com.netflix.graphql.dgs.DgsMutation;
import com.pml.identity.account.ContactService;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.service.PendingApprovalStatsService;
import com.pml.identity.service.UserService;
import com.pml.identity.service.UserStatsService;
import com.pml.identity.web.graphql.mutation.UserMutationResolver;
import com.pml.identity.web.graphql.query.UserQueryResolver;
import com.pml.identity.web.graphql.resolver.UserAccountFields;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import graphql.schema.DataFetchingEnvironment;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reading another person's account by its id is the object-level authorization flaw (CWE-639): an
 * id is not a secret, so a signed-in buyer must not be able to type one. {@code user(id)} is for
 * administrators, {@code me} finds the caller by the token's subject, and a contact - even masked -
 * is shown only to its holder and to administrators.
 *
 * <p>Method security is enforced here by a real proxy, not read off annotations.
 */
@Tag("L1")
@Tag("ET-IDN-004")
@Tag("ET-IDN-002")
@DisplayName("ET-IDN-004 · account resolvers: no IDOR, subject-based me, contacts for their holder and admins only")
class UserResolverAuthorizationTest {

    private UserService users;
    private ContactService contacts;
    private AnnotationConfigApplicationContext context;
    private UserQueryResolver queries;

    @Configuration
    @EnableReactiveMethodSecurity
    static class Beans {
        @Bean
        UserService users() {
            return Mockito.mock(UserService.class);
        }

        @Bean
        UserStatsService stats() {
            return Mockito.mock(UserStatsService.class);
        }

        @Bean
        PendingApprovalStatsService pending() {
            return Mockito.mock(PendingApprovalStatsService.class);
        }

        @Bean
        UserQueryResolver queries(UserService users, UserStatsService stats, PendingApprovalStatsService pending) {
            return new UserQueryResolver(users, stats, pending);
        }
    }

    @BeforeEach
    void start() {
        context = new AnnotationConfigApplicationContext(Beans.class);
        users = context.getBean(UserService.class);
        queries = context.getBean(UserQueryResolver.class);
        contacts = Mockito.mock(ContactService.class);
    }

    @AfterEach
    void stop() {
        context.close();
    }

    private static JwtAuthenticationToken caller(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).claim("realm_access", Map.of("roles", List.of(roles))).build();
        return new JwtAuthenticationToken(jwt, Arrays.stream(roles).map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList());
    }

    private static <T> Mono<T> as(JwtAuthenticationToken caller, Mono<T> action) {
        return action.contextWrite(ReactiveSecurityContextHolder.withAuthentication(caller));
    }

    @Test
    @DisplayName("user(id) refuses a signed-in buyer, and answers an administrator")
    void userByIdIsAdminOnly() {
        when(users.findById("victim")).thenReturn(Mono.just(User.builder().id("victim").build()));

        assertThatThrownBy(() -> as(caller("buyer-1", "CUSTOMER"), queries.user("victim")).block())
                .isInstanceOfAny(AccessDeniedException.class, AuthorizationDeniedException.class);
        verify(users, never()).findById(anyString());

        assertThat(as(caller("admin-1", "ADMIN"), queries.user("victim")).block().getId()).isEqualTo("victim");
    }

    @Test
    @DisplayName("the lookups by email and by phone are administrators' too")
    void contactLookupsAreAdminOnly() {
        assertThatThrownBy(() -> as(caller("buyer-1", "CUSTOMER"), queries.userByEmail("a@example.com")).block())
                .isInstanceOfAny(AccessDeniedException.class, AuthorizationDeniedException.class);
        assertThatThrownBy(() -> as(caller("buyer-1", "CUSTOMER"), queries.userByPhone("+260971234567")).block())
                .isInstanceOfAny(AccessDeniedException.class, AuthorizationDeniedException.class);
    }

    @Test
    @DisplayName("me finds the caller by the token's subject - by id or by Keycloak link - and never by email")
    void meUsesTheSubject() {
        when(users.findBySubject("kc-sub-1")).thenReturn(Mono.just(User.builder().id("account-1").build()));

        assertThat(as(caller("kc-sub-1", "CUSTOMER"), queries.me()).block().getId()).isEqualTo("account-1");

        verify(users).findBySubject("kc-sub-1");
        verify(users, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("me needs a signed-in caller")
    void meNeedsAToken() {
        assertThatThrownBy(() -> queries.me().block())
                .isInstanceOfAny(AccessDeniedException.class, AuthorizationDeniedException.class);
    }

    // ---- contacts ------------------------------------------------------------------------------------

    private Mono<List<Contact>> contactsOf(User account, JwtAuthenticationToken caller) {
        UserAccountFields fields = new UserAccountFields(contacts, Mockito.mock(com.pml.identity.service.AccountLifecycleService.class));
        DgsDataFetchingEnvironment environment = Mockito.mock(DgsDataFetchingEnvironment.class);
        when(environment.getSource()).thenReturn(account);
        return as(caller, fields.contacts(environment));
    }

    @Test
    @DisplayName("contacts are shown to their holder - by account id or by Keycloak id - and to administrators")
    void contactsForHolderAndAdmin() {
        Contact masked = Contact.builder().id("c-1").type(ContactType.WHATSAPP).valueMasked("+260 97* ***567").build();
        when(contacts.contactsOf("account-1")).thenReturn(Flux.just(masked));
        User account = User.builder().id("account-1").keycloakUserId("kc-1").build();

        assertThat(contactsOf(account, caller("account-1", "CUSTOMER")).block()).containsExactly(masked);
        assertThat(contactsOf(account, caller("kc-1", "CUSTOMER")).block()).containsExactly(masked);
        assertThat(contactsOf(account, caller("admin-9", "ADMIN")).block()).containsExactly(masked);
    }

    @Test
    @DisplayName("contacts are refused to another buyer and to an unauthenticated caller")
    void contactsForNobodyElse() {
        User account = User.builder().id("account-1").keycloakUserId("kc-1").build();

        assertThatThrownBy(() -> contactsOf(account, caller("somebody-else", "CUSTOMER")).block())
                .isInstanceOf(DomainRefusal.class)
                .extracting(error -> ((DomainRefusal) error).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        verify(contacts, never()).contactsOf(anyString());

        UserAccountFields fields = new UserAccountFields(contacts, Mockito.mock(com.pml.identity.service.AccountLifecycleService.class));
        DgsDataFetchingEnvironment environment = Mockito.mock(DgsDataFetchingEnvironment.class);
        when(environment.getSource()).thenReturn(account);
        assertThatThrownBy(() -> fields.contacts(environment).block())
                .isInstanceOf(DomainRefusal.class)
                .extracting(error -> ((DomainRefusal) error).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_AUTHENTICATED);
    }

    // ---- the mutation surface -----------------------------------------------------------------------

    @Test
    @DisplayName("every administrative mutation requires ADMIN or SUPER_ADMIN; the profile mutation requires only a caller")
    void mutationGuards() {
        for (Method method : UserMutationResolver.class.getDeclaredMethods()) {
            if (method.getAnnotation(DgsMutation.class) == null) {
                continue;
            }
            PreAuthorize guard = method.getAnnotation(PreAuthorize.class);
            assertThat(guard).as(method.getName() + " is guarded").isNotNull();
            if (method.getName().equals("updateMyProfile")) {
                assertThat(guard.value()).isEqualTo("isAuthenticated()");
            } else {
                assertThat(guard.value()).as(method.getName()).contains("ADMIN");
            }
        }
    }

    @Test
    @DisplayName("no operation for a buyer to prove or change a contact through their profile exists")
    void noContactWritesOnTheProfile() {
        assertThat(Arrays.stream(UserMutationResolver.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("sendPhoneVerification", "verifyPhone", "sendEmailVerification", "verifyEmail",
                        "changePassword", "resetPassword", "syncEmailVerificationStatus");
        assertThat(Arrays.stream(com.pml.identity.web.graphql.dto.UpdateUserInput.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactlyInAnyOrder("firstName", "lastName", "displayName", "gender");
    }
}
