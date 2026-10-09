package com.pml.shared.testing;

import com.netflix.graphql.dgs.DgsData;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Compares the Java classes behind GraphQL types with the schema, field by field.
 *
 * <p>DGS resolves a field by reading the property of the same name, and binds an input by
 * setting the property of the same name. A name that differs on either side fails silently:
 * an output field the class lacks comes back null (an error when the field is non-null), an
 * input field the class lacks is dropped, and an input property the schema lacks can never be
 * set. A class is matched to the schema type with its simple name.
 *
 * <p>An output property the schema lacks is not reported: a field resolver may read it under
 * another name, which only the Java call graph can tell.
 */
public final class GraphQlSchemaParity {

    private GraphQlSchemaParity() {
    }

    /** The {@code kind Type.field} prefix of a drift line, stable while its explanation changes. */
    public static String key(String driftLine) {
        String[] words = driftLine.split(" ", 3);
        return words[0] + " " + words[1];
    }

    /**
     * Every mismatch between the schema and the classes of {@code dtoPackage}, one line each.
     *
     * @param resolverPackage where the {@code @DgsData} field resolvers live; a schema field
     *                        with a resolver needs no property
     */
    public static List<String> drift(String schemaLocation, String dtoPackage, String resolverPackage) {
        TypeDefinitionRegistry schema = parse(schemaLocation);
        Map<String, Set<String>> outputs = new HashMap<>();
        schema.getTypes(ObjectTypeDefinition.class).forEach(type -> outputs
                .computeIfAbsent(type.getName(), k -> new TreeSet<>())
                .addAll(type.getFieldDefinitions().stream().map(FieldDefinition::getName).toList()));
        schema.objectTypeExtensions().forEach((name, extensions) -> extensions.forEach(extension -> outputs
                .computeIfAbsent(name, k -> new TreeSet<>())
                .addAll(extension.getFieldDefinitions().stream().map(FieldDefinition::getName).toList())));
        Map<String, Set<String>> inputs = new HashMap<>();
        schema.getTypes(InputObjectTypeDefinition.class).forEach(type -> inputs
                .computeIfAbsent(type.getName(), k -> new TreeSet<>())
                .addAll(type.getInputValueDefinitions().stream().map(InputValueDefinition::getName).toList()));

        Map<String, Set<String>> resolved = fieldResolvers(resolverPackage);
        List<String> drift = new ArrayList<>();
        for (Class<?> dto : classesIn(dtoPackage)) {
            String type = dto.getSimpleName();
            Set<String> declared = declaredProperties(dto);
            if (inputs.containsKey(type)) {
                Set<String> schemaFields = inputs.get(type);
                difference(schemaFields, declared).forEach(field ->
                        drift.add("input " + type + "." + field + " is in the schema; " + dto.getName() + " drops it"));
                difference(declared, schemaFields).forEach(field ->
                        drift.add("input " + type + "." + field + " is on " + dto.getName() + "; no client can set it"));
            } else if (outputs.containsKey(type)) {
                Set<String> schemaFields = outputs.get(type);
                Set<String> readable = readableProperties(dto);
                readable.addAll(resolved.getOrDefault(type, Set.of()));
                difference(schemaFields, readable).forEach(field ->
                        drift.add("type " + type + "." + field + " is in the schema; " + dto.getName() + " has no such property"));
            }
        }
        return drift;
    }

    private static TypeDefinitionRegistry parse(String location) {
        try {
            StringBuilder sdl = new StringBuilder();
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources(location)) {
                sdl.append(resource.getContentAsString(StandardCharsets.UTF_8)).append('\n');
            }
            return new SchemaParser().parse(sdl.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Class<?>> classesIn(String basePackage) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
            Class<?> type = load(candidate.getBeanClassName());
            classes.add(type);
            for (Class<?> nested : type.getDeclaredClasses()) {
                if (Modifier.isStatic(nested.getModifiers()) && !nested.isEnum() && !nested.isInterface()) {
                    classes.add(nested);
                }
            }
        }
        return classes;
    }

    private static Map<String, Set<String>> fieldResolvers(String basePackage) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
        Map<String, Set<String>> resolved = new HashMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
            for (Method method : load(candidate.getBeanClassName()).getDeclaredMethods()) {
                DgsData data = method.getAnnotation(DgsData.class);
                if (data != null) {
                    String field = data.field().isEmpty() ? method.getName() : data.field();
                    resolved.computeIfAbsent(data.parentType(), k -> new HashSet<>()).add(field);
                }
                DgsData.List list = method.getAnnotation(DgsData.List.class);
                if (list != null) {
                    for (DgsData each : list.value()) {
                        String field = each.field().isEmpty() ? method.getName() : each.field();
                        resolved.computeIfAbsent(each.parentType(), k -> new HashSet<>()).add(field);
                    }
                }
            }
        }
        return resolved;
    }

    /** The state the class declares: record components, or instance fields. */
    private static Set<String> declaredProperties(Class<?> type) {
        Set<String> names = new TreeSet<>();
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                names.add(component.getName());
            }
            return names;
        }
        for (var field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                names.add(field.getName());
            }
        }
        return names;
    }

    /** What DGS can read: declared state plus every public no-argument getter or accessor. */
    private static Set<String> readableProperties(Class<?> type) {
        Set<String> names = declaredProperties(type);
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())
                    || method.getDeclaringClass() == Object.class) {
                continue;
            }
            String name = method.getName();
            names.add(name);
            if (name.startsWith("get") && name.length() > 3) {
                names.add(Character.toLowerCase(name.charAt(3)) + name.substring(4));
            } else if (name.startsWith("is") && name.length() > 2) {
                names.add(Character.toLowerCase(name.charAt(2)) + name.substring(3));
            }
        }
        return names;
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> out = new TreeSet<>(left);
        out.removeAll(right);
        return out;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, GraphQlSchemaParity.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
