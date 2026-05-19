package com.pml.booking.config;

import graphql.schema.idl.RuntimeWiring;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * DGS GraphQL Configuration
 *
 * Custom scalar types (DateTime, BigDecimal, JSON) are automatically registered
 * by DGS Extended Scalars Auto Configuration when the graphql-dgs-extended-scalars
 * dependency is present. No manual registration needed.
 *
 * DGS 10.x integrates with Spring for GraphQL while providing Apollo Federation support.
 * All resolvers use @DgsComponent annotations for automatic wiring.
 */
@Configuration
public class GraphQLConfig {

    /**
     * RuntimeWiringConfigurer bean to satisfy Spring for GraphQL's wiring requirements.
     * DGS handles all actual wiring via @DgsComponent annotations, so this is a no-op.
     */
    @Bean
    @Primary
    public RuntimeWiringConfigurer dgsRuntimeWiringConfigurer() {
        return wiringBuilder -> {
            // No-op - DGS handles all wiring via @DgsComponent annotations
        };
    }
}
