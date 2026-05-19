package com.pml.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * DGS GraphQL Configuration
 *
 * This service uses DGS annotations exclusively (@DgsComponent, @DgsQuery, @DgsMutation)
 * for proper Apollo Federation integration.
 *
 * Custom scalar types (DateTime, BigDecimal, JSON) are automatically registered
 * by DGS Extended Scalars Auto Configuration when the graphql-dgs-extended-scalars
 * dependency is present.
 *
 * DGS 10.x provides deep integration with Spring for GraphQL while supporting
 * Apollo Federation for distributed GraphQL architectures.
 */
@Configuration
public class GraphQLConfig {

    /**
     * Override the shared-library's RuntimeWiringConfigurer with an empty one
     * to let DGS handle all GraphQL wiring exclusively.
     * This prevents conflicts between Spring GraphQL RuntimeWiringConfigurer
     * and DGS's internal wiring mechanism.
     */
    @Bean
    @Primary
    public RuntimeWiringConfigurer dgsRuntimeWiringConfigurer() {
        return wiringBuilder -> {
            // No-op - DGS handles all wiring via @DgsComponent annotations
        };
    }
}
