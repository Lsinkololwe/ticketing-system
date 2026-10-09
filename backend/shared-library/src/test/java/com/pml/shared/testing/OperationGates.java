package com.pml.shared.testing;

import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Finds the queries and mutations a service exposes with no access decision recorded anywhere.
 *
 * <p>A root field is decided when its schema definition carries {@code @auth} (the directive
 * wraps the data fetcher and refuses before any resolver code runs) or when its resolver method,
 * or the resolver class, carries {@code @PreAuthorize} (method security refuses before the body
 * runs). A field that is meant to be open must say so with {@code @auth(requires: PUBLIC)}, so
 * "forgotten" and "deliberately open" are distinguishable on the page and in this report.</p>
 */
public final class OperationGates {

    private OperationGates() {
    }

    /** {@code Type.field} for every root field with neither an {@code @auth} nor a {@code @PreAuthorize}. */
    public static List<String> undecided(Path schema, Path resolverSources, Path javaRoot) {
        Set<String> withDirective = new HashSet<>();
        Set<String> rootFields = new TreeSet<>();
        TypeDefinitionRegistry registry = new SchemaParser().parse(read(schema));
        collect(registry, "Query", rootFields, withDirective);
        collect(registry, "Mutation", rootFields, withDirective);

        Set<String> withAnnotation = new HashSet<>();
        try (Stream<Path> files = Files.walk(resolverSources)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Class<?> type = load(javaRoot.relativize(file));
                for (Method method : type.getDeclaredMethods()) {
                    String coordinate = coordinate(method);
                    boolean gated = method.isAnnotationPresent(PreAuthorize.class)
                            || type.isAnnotationPresent(PreAuthorize.class);
                    if (coordinate != null && gated) {
                        withAnnotation.add(coordinate);
                    }
                }
            }
        } catch (IOException unreadable) {
            throw new AssertionError("resolver sources could not be read: " + resolverSources, unreadable);
        }

        List<String> undecided = new ArrayList<>();
        for (String field : rootFields) {
            if (!withDirective.contains(field) && !withAnnotation.contains(field)) {
                undecided.add(field);
            }
        }
        return undecided;
    }

    private static void collect(TypeDefinitionRegistry registry, String root, Set<String> fields, Set<String> directed) {
        List<FieldDefinition> definitions = new ArrayList<>();
        registry.getType(root, ObjectTypeDefinition.class)
                .ifPresent(type -> definitions.addAll(type.getFieldDefinitions()));
        registry.objectTypeExtensions().getOrDefault(root, List.of())
                .forEach((ObjectTypeExtensionDefinition extension) -> definitions.addAll(extension.getFieldDefinitions()));
        for (FieldDefinition definition : definitions) {
            String coordinate = root + "." + definition.getName();
            fields.add(coordinate);
            if (!definition.getDirectives("auth").isEmpty()) {
                directed.add(coordinate);
            }
        }
    }

    /** The schema coordinate a resolver method answers, or null for a method that is not a root resolver. */
    private static String coordinate(Method method) {
        DgsQuery query = method.getAnnotation(DgsQuery.class);
        if (query != null) {
            return "Query." + (query.field().isEmpty() ? method.getName() : query.field());
        }
        DgsMutation mutation = method.getAnnotation(DgsMutation.class);
        if (mutation != null) {
            return "Mutation." + (mutation.field().isEmpty() ? method.getName() : mutation.field());
        }
        DgsData data = method.getAnnotation(DgsData.class);
        if (data != null && ("Query".equals(data.parentType()) || "Mutation".equals(data.parentType()))) {
            return data.parentType() + "." + (data.field().isEmpty() ? method.getName() : data.field());
        }
        return null;
    }

    private static Class<?> load(Path relativeSource) {
        String name = relativeSource.toString().replace('/', '.').replaceAll("\\.java$", "");
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("resolver class not loadable: " + name, missing);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException unreadable) {
            throw new AssertionError("schema could not be read: " + file, unreadable);
        }
    }
}
