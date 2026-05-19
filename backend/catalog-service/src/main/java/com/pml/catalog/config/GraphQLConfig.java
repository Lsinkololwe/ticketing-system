package com.pml.catalog.config;

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
 */
@Configuration
public class GraphQLConfig {

    /**
     * Provides a no-op RuntimeWiringConfigurer to avoid conflicts between
     * Spring for GraphQL and DGS framework. DGS handles all wiring via
     * @DgsComponent annotations.
     *
     * @return a no-op RuntimeWiringConfigurer
     */
    @Bean
    @Primary
    public RuntimeWiringConfigurer dgsRuntimeWiringConfigurer() {
        return wiringBuilder -> {
            // No-op - DGS handles all wiring via @DgsComponent annotations
        };
    }
}
