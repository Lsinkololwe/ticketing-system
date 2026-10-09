package com.pml.booking.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.config.MongoSchemaValidationProperties;
import com.pml.shared.persistence.MoneyConversions;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * For every collection that has a validator under {@code mongodb/schemas}, a fully populated
 * instance of its model is saved through the real mapping converter (money as Decimal128, as in
 * production) into a real replica set whose validators were applied exactly as startup applies
 * them. The write must be accepted and read back.
 *
 * <p>{@link BookingModelValidatorParityTest} compares field <em>names</em>; this proves the
 * <em>types and values</em> agree too: a {@code Decimal128} money field typed {@code double}, a
 * boolean where the model writes a date, an enum constant the validator does not list.
 */
@Tag("L2")
@Tag("ET-PLT-010")
@DisplayName("F-031 · a fully populated instance of every validated booking model is accepted by its real validator")
class BookingValidatorRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant WHEN = Instant.parse("2026-10-05T08:00:00Z");

    /** Values a generic generator cannot guess because the validator constrains the shape. */
    private static final Map<String, String> SHAPED = Map.ofEntries(
            Map.entry("email", "person@example.com"),
            Map.entry("phoneNumber", "+260971234567"),
            Map.entry("phone", "+260971234567"),
            // Platform account ids are UUIDs, not ObjectIds: every user-ish id must be accepted in that form.
            Map.entry("organizerId", "11111111-1111-4111-8111-111111111111"),
            Map.entry("buyerId", "22222222-2222-4222-8222-222222222222"),
            Map.entry("userId", "33333333-3333-4333-8333-333333333333"),
            Map.entry("customerId", "44444444-4444-4444-8444-444444444444"),
            Map.entry("createdBy", "55555555-5555-4555-8555-555555555555"),
            Map.entry("updatedBy", "55555555-5555-4555-8555-555555555555"),
            Map.entry("processedBy", "55555555-5555-4555-8555-555555555555"));

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static Map<String, String> schemaFiles;

    @BeforeAll
    static void applyValidators() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        SimpleReactiveMongoDatabaseFactory factory = new SimpleReactiveMongoDatabaseFactory(client, "booking_roundtrip");
        MongoCustomConversions conversions = new MoneyConversions().mongoCustomConversions();
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        template = new ReactiveMongoTemplate(factory, converter);
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();

        new MongoSchemaValidationConfig(template, new DefaultResourceLoader(), new MongoSchemaValidationProperties())
                .applySchemaValidation();
        schemaFiles = new Accessor().schemaDefinitions();
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @TestFactory
    Stream<DynamicTest> everyValidatedModelRoundTrips() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Document.class));
        List<DynamicTest> tests = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.pml.booking")) {
            Class<?> model = classFor(candidate.getBeanClassName());
            Document annotation = model.getAnnotation(Document.class);
            String collection = !annotation.collection().isEmpty() ? annotation.collection() : annotation.value();
            String schemaFile = schemaFiles.get(collection);
            if (schemaFile == null) {
                continue;
            }
            covered.add(collection);
            tests.add(DynamicTest.dynamicTest(model.getSimpleName() + " -> " + collection, () -> roundTrip(model, collection, schemaFile)));
        }
        tests.add(DynamicTest.dynamicTest("every registered validator has a model under test", () ->
                assertThat(covered).as("collections with a schema but no @Document model found").containsAll(schemaFiles.keySet())));
        return tests.stream();
    }

    private void roundTrip(Class<?> model, String collection, String schemaFile) throws Exception {
        JsonNode properties = loadSchema(schemaFile).path("properties");
        Object instance = populate(model, properties, new ArrayList<>());
        Object saved;
        try {
            saved = template.save(instance, collection).block();
        } catch (RuntimeException failure) {
            throw new AssertionError("validator '%s' refused a fully populated %s: %s".formatted(
                    schemaFile, model.getSimpleName(), failure.getMessage()), failure);
        }
        assertThat(saved).isNotNull();
        Object id = idOf(saved);
        assertThat(template.findById(id, model, collection).block())
                .as("%s reads back from %s", model.getSimpleName(), collection).isNotNull();
    }

    // ---------------------------------------------------------------------------- generation

    private static Object populate(Class<?> type, JsonNode properties, List<Class<?>> path) throws Exception {
        path.add(type);
        try {
            if (type.isRecord()) {
                var components = type.getRecordComponents();
                Class<?>[] types = new Class<?>[components.length];
                Object[] values = new Object[components.length];
                for (int i = 0; i < components.length; i++) {
                    types[i] = components[i].getType();
                    values[i] = value(types[i], components[i].getGenericType(), components[i].getName(),
                            properties.path(components[i].getName()), path);
                    if (values[i] == null && types[i].isPrimitive()) {
                        values[i] = java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(types[i], 1), 0);
                    }
                }
                var constructor = type.getDeclaredConstructor(types);
                constructor.setAccessible(true);
                return constructor.newInstance(values);
            }
            Object instance;
            try {
                var constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                instance = constructor.newInstance();
            } catch (NoSuchMethodException noDefault) {
                instance = new org.springframework.objenesis.ObjenesisStd().newInstance(type);
            }
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic() || Modifier.isTransient(field.getModifiers())
                            || field.isAnnotationPresent(Transient.class)
                            // a non-null version marks the entity as already stored: left null, the save is an insert
                            || field.isAnnotationPresent(org.springframework.data.annotation.Version.class)) {
                        continue;
                    }
                    field.setAccessible(true);
                    boolean isId = field.isAnnotationPresent(org.springframework.data.annotation.Id.class);
                    JsonNode hint = properties.path(isId ? "_id" : field.getName());
                    Object value = value(field.getType(), field.getGenericType(), field.getName(), hint, path);
                    if (value != null) {
                        field.set(instance, value);
                    }
                }
            }
            return instance;
        } finally {
            path.remove(path.size() - 1);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object value(Class<?> type, Type generic, String name, JsonNode hint, List<Class<?>> path) throws Exception {
        if (type == String.class) {
            if (hint.path("enum").isArray() && hint.path("enum").size() > 0) {
                return hint.path("enum").get(0).asText();
            }
            if (hint.path("bsonType").asText("").equals("objectId")) {
                return objectId();
            }
            if (hint.path("pattern").isTextual()) {
                String shaped = fromPattern(hint.path("pattern").asText());
                if (shaped != null) {
                    int min = hint.path("minLength").asInt(0);
                    while (shaped.length() < min && !shaped.isEmpty()) {
                        shaped = shaped + shaped.charAt(shaped.length() - 1);
                    }
                    return shaped;
                }
            }
            String shapedByName = SHAPED.get(name);
            if (shapedByName == null && name.toLowerCase().endsWith("email")) {
                shapedByName = "person@example.com";
            }
            if (shapedByName == null && name.toLowerCase().endsWith("phone")) {
                shapedByName = "+260971234567";
            }
            if (shapedByName != null) {
                return shapedByName;
            }
            int min = hint.path("minLength").asInt(0);
            String base = name.equals("id") ? UUID.randomUUID().toString() : "sample-" + name;
            int max = hint.path("maxLength").asInt(Integer.MAX_VALUE);
            base = base.length() >= min ? base : base + "x".repeat(min - base.length());
            return base.length() <= max ? base : base.substring(0, max);
        }
        if (type == boolean.class || type == Boolean.class) {
            return true;
        }
        if (type == int.class || type == Integer.class) {
            return (int) Math.min(Math.max(1, hint.path("minimum").asLong(1)), hint.path("maximum").asLong(Long.MAX_VALUE));
        }
        if (type == long.class || type == Long.class) {
            return Math.min(Math.max(1L, hint.path("minimum").asLong(1)), hint.path("maximum").asLong(Long.MAX_VALUE));
        }
        if (type == double.class || type == Double.class) {
            return Math.min(Math.max(0.5d, hint.path("minimum").asDouble(0.5d)), hint.path("maximum").asDouble(Double.MAX_VALUE));
        }
        if (type == float.class || type == Float.class) {
            return 0.5f;
        }
        if (type == BigDecimal.class) {
            BigDecimal sample = new BigDecimal("125.50").max(hint.path("minimum").isNumber()
                    ? new BigDecimal(hint.path("minimum").asText()) : BigDecimal.ZERO);
            return hint.path("maximum").isNumber() ? sample.min(new BigDecimal(hint.path("maximum").asText())) : sample;
        }
        if (type == Instant.class) {
            return WHEN;
        }
        if (type == Date.class) {
            return Date.from(WHEN);
        }
        if (type == LocalDate.class) {
            return LocalDate.of(2026, 10, 5);
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.of(2026, 10, 5, 8, 0);
        }
        if (type == LocalTime.class) {
            return LocalTime.of(8, 0);
        }
        if (type == Duration.class) {
            return Duration.ofMinutes(5);
        }
        if (type == UUID.class) {
            return UUID.randomUUID();
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants.length == 0 ? null : constants[0];
        }
        if (type.isArray() && type.getComponentType() == byte.class) {
            return new byte[]{1, 2, 3};
        }
        if (Collection.class.isAssignableFrom(type)) {
            Collection collection = Set.class.isAssignableFrom(type)
                    ? (type.isAssignableFrom(HashSet.class) ? new HashSet() : java.util.EnumSet.noneOf((Class) enumArg(generic)))
                    : new ArrayList();
            Class<?> element = elementClass(generic, 0);
            Object item = element == null ? null
                    : value(element, element, name, hint.path("items"), path);
            if (item != null) {
                collection.add(item);
                for (int i = 1; i < hint.path("minItems").asInt(1); i++) {
                    Object more = value(element, element, name, hint.path("items"), path);
                    if (more != null) {
                        collection.add(more);
                    }
                }
            }
            return collection;
        }
        if (Map.class.isAssignableFrom(type)) {
            Map map = new LinkedHashMap();
            Class<?> key = elementClass(generic, 0);
            Class<?> val = elementClass(generic, 1);
            Object k = key == null ? "key" : value(key, key, name + "Key", new com.fasterxml.jackson.databind.node.ObjectNode(MAPPER.getNodeFactory()), path);
            Object v = val == null ? "value" : value(val, val, name + "Value", new com.fasterxml.jackson.databind.node.ObjectNode(MAPPER.getNodeFactory()), path);
            if (k != null && v != null) {
                map.put(k, v);
            }
            return map;
        }
        if (type == Object.class) {
            return "sample-" + name;
        }
        if (type.getName().startsWith("java.") || path.contains(type) || path.size() > 6) {
            return null;
        }
        return populate(type, hint.path("properties"), path);
    }

    private static String objectId() {
        return new org.bson.types.ObjectId().toHexString();
    }

    /**
     * A value matching the validator's {@code pattern}: the shortest sample of the regex subset the
     * schemas use (literals, escapes, classes, groups, alternation, quantifiers). Null when the
     * pattern uses something outside that subset, in which case the plain sample is tried and the
     * validator's own message names the property.
     */
    static String fromPattern(String pattern) {
        try {
            String body = pattern.startsWith("^") ? pattern.substring(1) : pattern;
            body = body.endsWith("$") && !body.endsWith("\\$") ? body.substring(0, body.length() - 1) : body;
            int[] at = {0};
            return sampleAlternation(body, at);
        } catch (RuntimeException unsupported) {
            return null;
        }
    }

    private static String sampleAlternation(String p, int[] at) {
        String first = sampleSequence(p, at);
        while (at[0] < p.length() && p.charAt(at[0]) == '|') {
            at[0]++;
            sampleSequence(p, at);
        }
        return first;
    }

    private static String sampleSequence(String p, int[] at) {
        StringBuilder out = new StringBuilder();
        while (at[0] < p.length() && p.charAt(at[0]) != '|' && p.charAt(at[0]) != ')') {
            String atom;
            char c = p.charAt(at[0]++);
            if (c == '(') {
                if (p.startsWith("?:", at[0])) {
                    at[0] += 2;
                }
                atom = sampleAlternation(p, at);
                at[0]++; // the closing parenthesis
            } else if (c == '[') {
                int end = p.indexOf(']', at[0] + 1);
                String set = p.substring(at[0], end);
                at[0] = end + 1;
                if (set.startsWith("^")) {
                    throw new IllegalArgumentException("negated class");
                }
                atom = set.startsWith("\\") ? escaped(set.charAt(1)) : String.valueOf(set.charAt(0));
            } else if (c == '\\') {
                atom = escaped(p.charAt(at[0]++));
            } else if (c == '.') {
                atom = "a";
            } else {
                atom = String.valueOf(c);
            }
            int count = 1;
            if (at[0] < p.length()) {
                char q = p.charAt(at[0]);
                if (q == '{') {
                    int end = p.indexOf('}', at[0]);
                    count = Integer.parseInt(p.substring(at[0] + 1, end).split(",")[0].trim());
                    at[0] = end + 1;
                } else if (q == '+') {
                    at[0]++;
                } else if (q == '*' || q == '?') {
                    count = 0;
                    at[0]++;
                }
            }
            out.append(atom.repeat(count));
        }
        return out.toString();
    }

    private static String escaped(char c) {
        return switch (c) {
            case 'd' -> "1";
            case 'w' -> "a";
            case 's' -> " ";
            default -> String.valueOf(c);
        };
    }

    private static Class<?> enumArg(Type generic) {
        Class<?> element = elementClass(generic, 0);
        return element != null && element.isEnum() ? element : null;
    }

    private static Class<?> elementClass(Type generic, int index) {
        if (generic instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length > index) {
            Type argument = parameterized.getActualTypeArguments()[index];
            if (argument instanceof Class<?> clazz) {
                return clazz;
            }
            if (argument instanceof ParameterizedType nested && nested.getRawType() instanceof Class<?> raw) {
                return raw;
            }
        }
        return null;
    }

    private static Object idOf(Object saved) throws Exception {
        for (Class<?> c = saved.getClass(); c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(org.springframework.data.annotation.Id.class)) {
                    field.setAccessible(true);
                    return field.get(saved);
                }
            }
        }
        throw new IllegalStateException(saved.getClass() + " has no @Id");
    }

    private static JsonNode loadSchema(String schemaFile) throws Exception {
        try (InputStream in = BookingValidatorRoundTripTest.class.getClassLoader()
                .getResourceAsStream("mongodb/schemas/" + schemaFile)) {
            assertThat(in).as("schema file %s", schemaFile).isNotNull();
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

    private static final class Accessor extends MongoSchemaValidationConfig {
        Accessor() {
            super(null, null, null);
        }

        Map<String, String> schemaDefinitions() {
            return getSchemaDefinitions();
        }
    }
}
