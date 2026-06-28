package com.pml.shared.config;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Shared GraphQL Scalar Configuration
 *
 * <p>Registers common value scalars used across all microservices:</p>
 * <ul>
 *   <li>{@code BigDecimal} — monetary values</li>
 *   <li>{@code JSON} — flexible JSON objects</li>
 * </ul>
 *
 * <p>{@code DateTime} is intentionally NOT registered here. It is provided by
 * {@link com.pml.shared.graphql.DateTimeScalar} ({@code @DgsScalar}), which
 * serializes {@link java.time.Instant} — the type the domain models use.
 * Registering it both here and via {@code @DgsScalar} causes a
 * {@code StrictModeWiringException: The scalar DateTime is already defined}.
 * The DGS built-in DateTime scalar must also be disabled via
 * {@code dgs.graphql.extensions.scalars.time-dates.enabled=false}.</p>
 */
@Configuration
public class SharedScalarConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(ExtendedScalars.GraphQLBigDecimal)
                .scalar(ExtendedScalars.Json);
    }
}
