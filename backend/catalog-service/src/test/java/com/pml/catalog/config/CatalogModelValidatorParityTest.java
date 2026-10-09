package com.pml.catalog.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every field a {@code @Document} model writes must be a property the
 * collection's JSON-schema validator allows.
 *
 * <p>Every validator in {@code mongodb/schemas/} is loaded with {@code additionalProperties: false}
 * and {@code validationAction: ERROR} (see {@code application.yml}). A validator that omits a field
 * the model actually persists does not fail a test — the fixtures in this suite use a template with
 * no validator — it fails every real write in any environment where the validator is enforced,
 * silently, one document at a time. This test is the parity check that catches that drift before it
 * reaches such an environment.
 *
 * <p>This is a one-directional check: every model field must be an allowed validator property. The
 * reverse (every validator property backed by a model field) is not asserted, since a validator may
 * legitimately describe optional or historical shapes a given model version does not use.
 */
@Tag("L1")
@Tag("ET-PLT-010")
@DisplayName("F-031 · catalog @Document models agree with their JSON-schema validators")
class CatalogModelValidatorParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static Map<String, String> schemaFiles;

    @BeforeAll
    static void loadSchemaDefinitions() {
        schemaFiles = new MongoSchemaValidationConfigTestAccessor().schemaDefinitions();
    }

    @TestFactory
    Stream<DynamicTest> everyDocumentFieldIsAnAllowedValidatorProperty() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Document.class));

        List<DynamicTest> tests = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.pml.catalog")) {
            Class<?> modelClass = classFor(candidate.getBeanClassName());
            tests.add(DynamicTest.dynamicTest(modelClass.getSimpleName(), () -> assertParity(modelClass)));
        }
        assertThat(tests).as("no @Document classes found under com.pml.catalog — scan is broken").isNotEmpty();
        return tests.stream();
    }

    /**
     * An enum field's validator must accept every constant the model can write.
     *
     * <p>A constant added to the Java enum and not to the collection's {@code enum} list is a write
     * the database refuses with "Document failed validation" — found only when that constant is
     * first written: the chart-of-accounts seed failed on {@code VERIFICATION_EXPENSE} this way.
     */
    @TestFactory
    Stream<DynamicTest> everyEnumFieldsConstantsAreAcceptedByItsValidator() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Document.class));

        List<DynamicTest> tests = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.pml.catalog")) {
            Class<?> modelClass = classFor(candidate.getBeanClassName());
            tests.add(DynamicTest.dynamicTest(modelClass.getSimpleName(), () -> assertEnumsAccepted(modelClass)));
        }
        return tests.stream();
    }

    private void assertEnumsAccepted(Class<?> modelClass) throws Exception {
        Document annotation = modelClass.getAnnotation(Document.class);
        String collection = !annotation.collection().isEmpty() ? annotation.collection() : annotation.value();
        String schemaFile = schemaFiles.get(collection);
        Assumptions.assumeTrue(schemaFile != null, "no validator registered for " + collection);
        JsonNode properties = loadSchema(schemaFile).path("properties");

        List<String> refused = new ArrayList<>();
        for (var field : modelClass.getDeclaredFields()) {
            if (!field.getType().isEnum() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Field mapped = field.getAnnotation(Field.class);
            String name = mapped != null && !mapped.value().isEmpty() ? mapped.value() : field.getName();
            JsonNode allowed = properties.path(name).path("enum");
            if (!allowed.isArray()) {
                continue;
            }
            Set<String> accepted = new HashSet<>();
            allowed.forEach(value -> accepted.add(value.asText()));
            for (Object constant : field.getType().getEnumConstants()) {
                if (!accepted.contains(((Enum<?>) constant).name())) {
                    refused.add(name + "=" + ((Enum<?>) constant).name());
                }
            }
        }
        assertThat(refused)
                .as("'%s' (validator %s) refuses these values the model can write", modelClass.getSimpleName(), schemaFile)
                .isEmpty();
    }

    private void assertParity(Class<?> modelClass) throws Exception {
        Document annotation = modelClass.getAnnotation(Document.class);
        String collection = !annotation.collection().isEmpty() ? annotation.collection() : annotation.value();

        String schemaFile = schemaFiles.get(collection);
        Assumptions.assumeTrue(schemaFile != null,
                "collection '%s' (%s) has no schema registered — nothing to compare against; tracked separately, not an F-031 model/validator disagreement"
                        .formatted(collection, modelClass.getSimpleName()));

        JsonNode schema = loadSchema(schemaFile);
        JsonNode properties = schema.path("properties");
        boolean additionalPropertiesFalse = !schema.path("additionalProperties").asBoolean(true);

        Set<String> declaredProperties = new HashSet<>();
        for (Iterator<String> it = properties.fieldNames(); it.hasNext(); ) {
            declaredProperties.add(it.next());
        }

        List<String> missing = new ArrayList<>();
        for (String field : persistedFieldNames(modelClass)) {
            if (!declaredProperties.contains(field)) {
                missing.add(field);
            }
        }

        if (additionalPropertiesFalse) {
            assertThat(missing)
                    .as("'%s' (validator %s) declares additionalProperties:false but is missing these fields "
                            + "the model persists — every write of them is rejected", modelClass.getSimpleName(), schemaFile)
                    .isEmpty();
            assertThat(declaredProperties)
                    .as("'%s' must allow _class — Spring Data's mapping converter writes it on every save", schemaFile)
                    .contains("_class");
        }
    }

    private static Set<String> persistedFieldNames(Class<?> modelClass) {
        Set<String> names = new HashSet<>();
        for (var field : modelClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            if (field.isAnnotationPresent(Transient.class) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            if (field.isAnnotationPresent(Id.class)) {
                names.add("_id");
                continue;
            }
            Field fieldAnnotation = field.getAnnotation(Field.class);
            names.add(fieldAnnotation != null && !fieldAnnotation.value().isEmpty()
                    ? fieldAnnotation.value() : field.getName());
        }
        return names;
    }

    private static JsonNode loadSchema(String schemaFile) throws Exception {
        try (InputStream in = CatalogModelValidatorParityTest.class.getClassLoader()
                .getResourceAsStream("mongodb/schemas/" + schemaFile)) {
            assertThat(in).as("schema file mongodb/schemas/%s not found on the test classpath", schemaFile).isNotNull();
            JsonNode root = MAPPER.readTree(in);
            return root.has("$jsonSchema") ? root.get("$jsonSchema") : root;
        }
    }

    private static Class<?> classFor(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Exposes the package-protected schema map for this test, same package as the config class. */
    private static final class MongoSchemaValidationConfigTestAccessor extends MongoSchemaValidationConfig {
        MongoSchemaValidationConfigTestAccessor() {
            super(null, null, null);
        }

        Map<String, String> schemaDefinitions() {
            return getSchemaDefinitions();
        }
    }
}
