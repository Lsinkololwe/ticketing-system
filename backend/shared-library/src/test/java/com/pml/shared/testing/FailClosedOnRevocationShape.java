package com.pml.shared.testing;

import com.netflix.graphql.dgs.DgsMutation;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds a method the {@code @FailClosedOnRevocation} aspect cannot actually guard.
 *
 * <p>{@code RevocationGuardAspect.guard} throws {@code IllegalStateException} for anything
 * that isn't {@code Mono}-returning, and it throws on every call, not just a revoked one — a
 * {@code @DgsMutation} annotated at the method level, or inside a class carrying the annotation,
 * with a {@code Flux} (or any other non-{@code Mono}) return type is broken from the moment it
 * is annotated, whatever token calls it. A test that only checks the annotation is present (as
 * {@code SensitiveMutationsTest} does) cannot see this; this one checks the shape underneath it.
 */
public final class FailClosedOnRevocationShape {

    private FailClosedOnRevocationShape() {
    }

    /**
     * @param resolverSources the service's resolver source directory, e.g.
     *                        {@code src/main/java/.../web/graphql/mutation}
     * @param javaRoot        that service's {@code src/main/java}, to turn a file path into a
     *                        class name
     * @return one description per offending method; empty when every annotated mutation returns
     *         {@code Mono}
     */
    public static List<String> violations(Path resolverSources, Path javaRoot) {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(resolverSources)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Class<?> type = load(javaRoot.relativize(file));
                boolean classGuarded = type.isAnnotationPresent(FailClosedOnRevocation.class);
                for (Method method : type.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(DgsMutation.class)) {
                        continue;
                    }
                    boolean guarded = classGuarded || method.isAnnotationPresent(FailClosedOnRevocation.class);
                    if (guarded && !Mono.class.isAssignableFrom(method.getReturnType())) {
                        offenders.add(type.getSimpleName() + "#" + method.getName() + " returns "
                                + method.getReturnType().getSimpleName()
                                + " — @FailClosedOnRevocation throws IllegalStateException on every call "
                                + "to a method that doesn't return Mono");
                    }
                }
            }
        } catch (IOException unreadable) {
            throw new AssertionError("resolver sources could not be read: " + resolverSources, unreadable);
        }
        return offenders;
    }

    private static Class<?> load(Path relativeSource) {
        String name = relativeSource.toString().replace('/', '.').replaceAll("\\.java$", "");
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("resolver class not loadable: " + name, missing);
        }
    }
}
