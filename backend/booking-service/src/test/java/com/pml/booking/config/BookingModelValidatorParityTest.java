package com.pml.booking.config;

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
 * <p>See {@code com.pml.catalog.config.CatalogModelValidatorParityTest} for the full rationale;
 * this is the booking-service instance of the same check.
 */
@Tag("L1")
@Tag("ET-PLT-010")
@DisplayName("F-031 · booking @Document models agree with their JSON-schema validators")
class BookingModelValidatorParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> SKIP_COLLECTIONS = Set.of();
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
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.pml.booking")) {
            Class<?> modelClass = classFor(candidate.getBeanClassName());
            tests.add(DynamicTest.dynamicTest(modelClass.getSimpleName(), () -> assertParity(modelClass)));
        }
        assertThat(tests).as("no @Document classes found under com.pml.booking — scan is broken").isNotEmpty();
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
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.pml.booking")) {
            Class<?> modelClass = classFor(candidate.getBeanClassName());
            tests.add(DynamicTest.dynamicTest(modelClass.getSimpleName(), () -> assertEnumsAccepted(modelClass)));
        }
        return tests.stream();
    }

    private void assertEnumsAccepted(Class<?> modelClass) throws Exception {
        Document annotation = modelClass.getAnnotation(Document.class);
        String collection = !annotation.collection().isEmpty() ? annotation.collection() : annotation.value();
        String schemaFile = schemaFiles.get(collection);
        if (schemaFile == null) {
            assertDeclaredWithoutValidator(collection, modelClass);
            return;
        }
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

    /**
     * A collection with no registered validator is a statement, not a gap: the persistence baseline lists, per
     * collection, whether it has one. A collection the baseline says has a validator, or does not list at all,
     * fails here, so "no schema" can no longer hide behind a skipped test.
     */
    private static void assertDeclaredWithoutValidator(String collection, Class<?> modelClass) throws Exception {
        java.nio.file.Path baseline = java.nio.file.Path.of("../../specs/_platform/002-persistence-baseline/spec.md");
        Assumptions.assumeTrue(java.nio.file.Files.exists(baseline), "specs/ not checked out beside backend/");
        java.util.regex.Matcher row = java.util.regex.Pattern
                .compile("\\|\\s*`" + java.util.regex.Pattern.quote(collection) + "`\\s*\\|\\s*(\\*\\*yes\\*\\*|no)\\s*\\|")
                .matcher(java.nio.file.Files.readString(baseline));
        assertThat(row.find())
                .as("%s (%s) has no validator registered and no row in the persistence baseline's collection registry", collection, modelClass.getSimpleName())
                .isTrue();
        assertThat(row.group(1))
                .as("the baseline says %s has a validator, but none is registered in MongoSchemaValidationConfig", collection)
                .isEqualTo("no");
    }

    private void assertParity(Class<?> modelClass) throws Exception {
        Document annotation = modelClass.getAnnotation(Document.class);
        String collection = !annotation.collection().isEmpty() ? annotation.collection() : annotation.value();

        Assumptions.assumeFalse(SKIP_COLLECTIONS.contains(collection),
                "collection '%s' is on the deliberate skip list — see class javadoc".formatted(collection));

        String schemaFile = schemaFiles.get(collection);
        if (schemaFile == null) {
            assertDeclaredWithoutValidator(collection, modelClass);
            return;
        }

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
        try (InputStream in = BookingModelValidatorParityTest.class.getClassLoader()
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
