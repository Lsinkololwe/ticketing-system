package com.pml.shared.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * The platform's JSON conventions, applied to Spring Boot's own {@code ObjectMapper}.
 *
 * <p>A customizer rather than a replacement mapper: a {@code @Primary ObjectMapper} switches off
 * Boot's Jackson auto-configuration, and with it the modules and {@code spring.jackson.*}
 * properties everything else expects. Instants travel as ISO-8601 strings, an unknown property is
 * ignored so a client can evolve ahead of a service, and nulls are written because GraphQL clients
 * distinguish an absent field from a null one.
 */
@AutoConfiguration
@ConditionalOnClass(Jackson2ObjectMapperBuilder.class)
public class PlatformJacksonAutoConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer platformJsonConventions() {
        return builder -> builder
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .serializationInclusion(JsonInclude.Include.ALWAYS);
    }
}
