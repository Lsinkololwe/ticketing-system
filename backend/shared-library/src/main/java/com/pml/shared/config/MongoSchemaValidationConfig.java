package com.pml.shared.config;

import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ValidationOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Base configuration class for applying MongoDB JSON Schema validation at application startup.
 * <p>
 * This class provides common functionality for:
 * <ul>
 *     <li>Loading JSON schema files from classpath resources</li>
 *     <li>Creating collections with schema validation</li>
 *     <li>Updating existing collections with new schemas (via collMod)</li>
 *     <li>Graceful error handling that doesn't fail application startup</li>
 * </ul>
 * </p>
 * <p>
 * Subclasses should override {@link #getSchemaDefinitions()} to specify
 * which collections need validation and their schema file paths.
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * {@literal @}Configuration
 * public class BookingSchemaValidationConfig extends MongoSchemaValidationConfig {
 *
 *     {@literal @}Override
 *     protected Map&lt;String, String&gt; getSchemaDefinitions() {
 *         Map&lt;String, String&gt; schemas = new HashMap&lt;&gt;();
 *         schemas.put("tickets", "schemas/tickets-schema.json");
 *         schemas.put("payments", "schemas/payments-schema.json");
 *         return schemas;
 *     }
 * }
 * </pre>
 * </p>
 *
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public abstract class MongoSchemaValidationConfig {

    protected final ReactiveMongoTemplate mongoTemplate;
    protected final ResourceLoader resourceLoader;
    protected final MongoSchemaValidationProperties properties;

    /**
     * Applies MongoDB schema validation when application is ready.
     * <p>
     * Uses {@link ApplicationReadyEvent} to ensure MongoDB connection is established
     * and all beans are initialized before attempting schema application.
     * </p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void applySchemaValidation() {
        if (!properties.isEnabled()) {
            log.info("MongoDB schema validation is disabled");
            return;
        }

        log.info("Applying MongoDB schema validation with action: {}, level: {}",
                properties.getValidationAction(),
                properties.getValidationLevel());

        Map<String, String> schemas = getSchemaDefinitions();
        if (schemas.isEmpty()) {
            log.warn("No schema definitions provided for MongoDB validation");
            return;
        }

        Flux.fromIterable(schemas.entrySet())
                .flatMap(entry -> applySchemaToCollection(entry.getKey(), entry.getValue())
                        .timeout(Duration.ofMillis(properties.getOperationTimeoutMs()))
                        .onErrorResume(e -> {
                            log.error("Failed to apply schema to collection: {}", entry.getKey(), e);
                            return properties.isFailOnValidationError()
                                    ? Mono.error(e)
                                    : Mono.empty();
                        })
                )
                .doOnComplete(() -> log.info("MongoDB schema validation applied successfully to {} collections",
                        schemas.size()))
                .doOnError(e -> log.error("Failed to apply MongoDB schemas", e))
                .subscribe();
    }

    /**
     * Returns a map of collection names to their schema file paths.
     * <p>
     * Schema paths are relative to {@code classpath:mongodb/schemas/} by default,
     * but can be customized via {@link MongoSchemaValidationProperties#schemaBasePath}.
     * </p>
     *
     * @return Map of collection name → schema file path
     */
    protected abstract Map<String, String> getSchemaDefinitions();

    /**
     * Applies JSON schema validation to a single collection.
     * <p>
     * If the collection exists, updates its validation rules using collMod.
     * If the collection doesn't exist, creates it with validation rules.
     * </p>
     *
     * @param collectionName MongoDB collection name
     * @param schemaPath Path to JSON schema file (relative to classpath resources)
     * @return A Mono that completes when schema is applied
     */
    protected Mono<Void> applySchemaToCollection(String collectionName, String schemaPath) {
        return loadSchema(schemaPath)
                .flatMap(schema -> mongoTemplate.collectionExists(collectionName)
                        .flatMap(exists -> {
                            if (exists) {
                                log.debug("Updating schema validation for existing collection: {}",
                                        collectionName);
                                return updateCollectionSchema(collectionName, schema);
                            } else {
                                log.debug("Creating collection with schema validation: {}",
                                        collectionName);
                                return createCollectionWithSchema(collectionName, schema);
                            }
                        })
                )
                .doOnSuccess(v -> log.info("Schema validation applied to collection: {}", collectionName))
                .then();
    }

    /**
     * Loads a JSON schema from classpath resources.
     *
     * @param schemaPath Path to schema file (e.g., "schemas/tickets-schema.json")
     * @return A Mono emitting the parsed schema Document
     */
    protected Mono<Document> loadSchema(String schemaPath) {
        return Mono.fromCallable(() -> {
            String fullPath = String.format("classpath:%s/%s",
                    properties.getSchemaBasePath(),
                    schemaPath);

            Resource resource = resourceLoader.getResource(fullPath);
            if (!resource.exists()) {
                throw new IllegalArgumentException("Schema file not found: " + fullPath);
            }

            // Read schema file
            String schemaJson = resource.getContentAsString(StandardCharsets.UTF_8);

            // Parse to BSON Document
            Document schemaDoc = Document.parse(schemaJson);

            // MongoDB expects the schema to be under $jsonSchema key
            // If the file already has this wrapper, use as-is
            // Otherwise, assume the entire file is the schema content
            if (schemaDoc.containsKey("$jsonSchema")) {
                return schemaDoc;
            } else {
                return new Document("$jsonSchema", schemaDoc);
            }
        });
    }

    /**
     * Creates a new collection with JSON schema validation.
     *
     * @param collectionName Collection name
     * @param schema Schema document (must have $jsonSchema key)
     * @return A Mono that completes when collection is created
     */
    protected Mono<Void> createCollectionWithSchema(String collectionName, Document schema) {
        return Mono.fromCallable(() -> {
            // Create validation options
            ValidationOptions validationOptions = new ValidationOptions()
                    .validator(schema)
                    .validationAction(properties.getValidationAction())
                    .validationLevel(properties.getValidationLevel());

            // Create collection options
            CreateCollectionOptions options = new CreateCollectionOptions()
                    .validationOptions(validationOptions);

            return options;
        })
        .flatMap(options ->
                mongoTemplate.getMongoDatabase()
                        .flatMap(db -> Mono.from(db.createCollection(collectionName, options)))
        )
        .then();
    }

    /**
     * Updates an existing collection's schema validation using collMod command.
     * <p>
     * MongoDB collMod command structure:
     * <pre>
     * {
     *   collMod: "collectionName",
     *   validator: { $jsonSchema: {...} },
     *   validationAction: "error",
     *   validationLevel: "strict"
     * }
     * </pre>
     * </p>
     *
     * @param collectionName Collection name
     * @param schema Schema document (must have $jsonSchema key)
     * @return A Mono that completes when schema is updated
     */
    protected Mono<Void> updateCollectionSchema(String collectionName, Document schema) {
        return Mono.fromCallable(() -> {
            Document collModCommand = new Document()
                    .append("collMod", collectionName)
                    .append("validator", schema)
                    .append("validationAction", properties.getValidationAction().getValue())
                    .append("validationLevel", properties.getValidationLevel().getValue());

            return collModCommand;
        })
        .flatMap(command ->
                mongoTemplate.getMongoDatabase()
                        .flatMap(db -> Mono.from(db.runCommand(command)))
        )
        .then();
    }

    /**
     * Helper method for subclasses to build schema definition maps.
     *
     * @return A new HashMap for schema definitions
     */
    protected Map<String, String> newSchemaMap() {
        return new HashMap<>();
    }
}
