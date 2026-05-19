package com.pml.shared.config;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Shared GraphQL Scalar Configuration
 *
 * Provides common scalar types used across all microservices:
 * - DateTime: LocalDateTime in ISO 8601 format
 * - BigDecimal: For monetary values
 * - Json: For flexible JSON objects
 */
@Configuration
public class SharedScalarConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(createLocalDateTimeScalar())
                .scalar(ExtendedScalars.GraphQLBigDecimal)
                .scalar(ExtendedScalars.Json);
    }

    /**
     * Create a DateTime scalar that handles LocalDateTime
     */
    public static GraphQLScalarType createLocalDateTimeScalar() {
        return GraphQLScalarType.newScalar()
                .name("DateTime")
                .description("A date-time string in ISO 8601 format (LocalDateTime)")
                .coercing(new Coercing<LocalDateTime, String>() {

                    @Override
                    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                        if (dataFetcherResult instanceof LocalDateTime) {
                            return ((LocalDateTime) dataFetcherResult).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        } else if (dataFetcherResult instanceof OffsetDateTime) {
                            return ((OffsetDateTime) dataFetcherResult).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                        }
                        throw new CoercingSerializeException("Expected LocalDateTime or OffsetDateTime, but got: " + dataFetcherResult.getClass());
                    }

                    @Override
                    public LocalDateTime parseValue(Object input) throws CoercingParseValueException {
                        if (input instanceof String) {
                            try {
                                return LocalDateTime.parse((String) input, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                            } catch (DateTimeParseException e) {
                                try {
                                    return LocalDateTime.parse((String) input, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                                } catch (DateTimeParseException ex) {
                                    throw new CoercingParseValueException("Invalid DateTime format: " + input, ex);
                                }
                            }
                        }
                        throw new CoercingParseValueException("Expected String, but got: " + input.getClass());
                    }

                    @Override
                    public LocalDateTime parseLiteral(Object input) throws CoercingParseLiteralException {
                        if (input instanceof StringValue) {
                            return parseValue(((StringValue) input).getValue());
                        }
                        throw new CoercingParseLiteralException("Expected StringValue, but got: " + input.getClass());
                    }
                })
                .build();
    }
}
