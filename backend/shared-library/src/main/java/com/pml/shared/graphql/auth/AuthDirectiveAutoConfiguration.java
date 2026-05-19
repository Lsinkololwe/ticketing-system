package com.pml.shared.graphql.auth;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsRuntimeWiring;
import graphql.schema.idl.RuntimeWiring;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

/**
 * Auto-configuration for the GraphQL @auth directive.
 *
 * <h2>Overview</h2>
 * <p>
 * This configuration automatically enables the @auth directive for role-based
 * authorization in GraphQL operations when:
 * </p>
 * <ul>
 *   <li>Netflix DGS is on the classpath</li>
 *   <li>Spring Security reactive is available</li>
 *   <li>graphql.auth.enabled=true (default)</li>
 * </ul>
 *
 * <h2>Configuration Properties</h2>
 * <pre>
 * graphql:
 *   auth:
 *     enabled: true           # Enable/disable @auth directive (default: true)
 *     log-access-denied: true # Log access denied attempts (default: true)
 * </pre>
 *
 * <h2>Usage</h2>
 * <p>
 * Once enabled, add @auth directive to your GraphQL schemas:
 * </p>
 * <pre>
 * type Mutation {
 *   createEvent(input: CreateEventInput!): Event @auth(requires: ORGANIZER)
 *   purchaseTicket(input: PurchaseInput!): Ticket @auth(requires: CUSTOMER)
 *   deleteUser(id: ID!): Boolean @auth(requires: ADMIN)
 * }
 * </pre>
 *
 * <h2>IMPORTANT: Schema Setup</h2>
 * <p>
 * Each service must include the auth directive definition in their schema.
 * Copy or reference the auth.graphqls file from shared-library:
 * </p>
 * <pre>
 * # In your service's schema.graphqls, include:
 * directive @auth(requires: Role = AUTHENTICATED) on FIELD_DEFINITION | OBJECT
 *
 * enum Role {
 *   PUBLIC
 *   AUTHENTICATED
 *   CUSTOMER
 *   ORGANIZER
 *   ADMIN
 *   SUPER_ADMIN
 *   FINANCE
 *   INTERNAL
 * }
 * </pre>
 *
 * @see AuthDirective
 * @see Role
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({DgsComponent.class, ReactiveSecurityContextHolder.class})
@ConditionalOnProperty(prefix = "graphql.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuthDirectiveAutoConfiguration {

    /**
     * Creates the @auth directive wiring configurer.
     *
     * <p>
     * This bean is picked up by DGS to register the AuthDirective as a
     * SchemaDirectiveWiring, enabling the @auth directive in GraphQL schemas.
     * </p>
     *
     * @return An AuthDirectiveWiringConfigurer that registers the @auth directive
     */
    @Bean
    public AuthDirectiveWiringConfigurer authDirectiveWiringConfigurer() {
        log.info("GraphQL Auth: Enabling @auth directive for role-based authorization");
        return new AuthDirectiveWiringConfigurer();
    }

    /**
     * DGS component that registers the @auth directive wiring.
     *
     * <p>
     * This inner class is a DGS component that will be detected by the DGS
     * framework and its @DgsRuntimeWiring method will be called to register
     * the AuthDirective.
     * </p>
     */
    @DgsComponent
    public static class AuthDirectiveWiringConfigurer {

        /**
         * Adds the @auth directive to the GraphQL runtime wiring.
         *
         * @param builder The RuntimeWiring builder
         * @return The builder with the @auth directive registered
         */
        @DgsRuntimeWiring
        public RuntimeWiring.Builder addAuthDirective(RuntimeWiring.Builder builder) {
            log.debug("Registering @auth directive with GraphQL runtime");
            return builder.directive("auth", new AuthDirective());
        }
    }
}
