package com.pml.shared.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Shared Jackson Configuration
 *
 * Provides consistent JSON serialization/deserialization across all microservices.
 */
@Configuration
public class SharedJacksonConfig {

    /**
     * Primary ObjectMapper for general use
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register JavaTimeModule for JSR-310 support (LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());

        // Disable writing dates as timestamps - use ISO format
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Don't fail on unknown properties - allows for API evolution
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Include null fields by default
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);

        return mapper;
    }

    /**
     * GraphQL-specific ObjectMapper that always includes null fields.
     * Required for GraphQL spec compliance.
     */
    @Bean(name = "graphQlObjectMapper")
    public ObjectMapper graphQlObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // CRITICAL: Include null fields for GraphQL compliance
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);

        return mapper;
    }
}
