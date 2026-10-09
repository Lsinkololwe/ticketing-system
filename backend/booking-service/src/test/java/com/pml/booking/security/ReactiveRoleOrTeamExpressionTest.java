package com.pml.booking.security;

import com.pml.booking.infrastructure.client.IdentityServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "Holds the role, or belongs to the organizer's team" has to be ONE reactive expression. The
 * original {@code hasRole(..) or @organizationSecurityService.isOrganizerOrTeamMember(..)} made
 * every organizer-side query fail with ConverterNotFoundException (MonoJust to Boolean) under
 * reactive method security. This runs the real expression, through the real advisor.
 */
@Tag("L1")
@Tag("ET-FIN-001")
@DisplayName("ET-FIN-001 · role-or-team @PreAuthorize expressions evaluate under reactive method security")
class ReactiveRoleOrTeamExpressionTest {

    static class Target {
        @PreAuthorize("@organizationSecurityService.rolesOrTeamMember(authentication, 'ADMIN,FINANCE', #organizerId)")
        public Mono<String> read(String organizerId) {
            return Mono.just("rows");
        }
    }

    @Configuration
    @EnableReactiveMethodSecurity
    static class Config {
        @Bean(name = "organizationSecurityService")
        OrganizationSecurityService organizationSecurityService() {
            IdentityServiceClient client = mock(IdentityServiceClient.class);
            when(client.checkSameOrganization(any(), any()))
                    .thenReturn(Mono.just(new IdentityServiceClient.SharedOrganizationResponse(false, null)));
            return new OrganizationSecurityService(client);
        }

        @Bean
        Target target() {
            return new Target();
        }
    }

    private static JwtAuthenticationToken caller(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject).claim("sub", subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        return new JwtAuthenticationToken(jwt, List.of(roles).stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    @Test
    @DisplayName("an administrator and the organizer themselves are admitted; a stranger is refused, not crashed")
    void admitsRoleAndSelfRefusesStranger() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class)) {
            Target target = ctx.getBean(Target.class);
            StepVerifier.create(target.read("someone").contextWrite(ReactiveSecurityContextHolder
                            .withAuthentication(caller("11111111-1111-4111-8111-111111111111", "ADMIN"))))
                    .expectNext("rows").verifyComplete();
            String self = "22222222-2222-4222-8222-222222222222";
            StepVerifier.create(target.read(self).contextWrite(ReactiveSecurityContextHolder
                            .withAuthentication(caller(self, "ORGANIZER"))))
                    .expectNext("rows").verifyComplete();
            StepVerifier.create(target.read("someone-else").contextWrite(ReactiveSecurityContextHolder
                            .withAuthentication(caller("33333333-3333-4333-8333-333333333333", "ORGANIZER"))))
                    .expectError(AccessDeniedException.class).verify();
        }
    }
}
