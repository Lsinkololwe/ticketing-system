package com.pml.shared.error;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared assertion: every exception a service declares has a registry code.
 *
 * <h2>Why this is a scan and not a list</h2>
 * A hand-written list of exception classes is a second copy of the package
 * contents, and it is the copy that goes stale — the new exception class is
 * added, the list is not, and nothing fails. Scanning the package means the
 * test learns about the new class the moment it compiles.
 *
 * <p>What it catches is quiet. An unmapped exception is not an error at
 * startup or a broken build; it simply falls through to the defect path, so a
 * routine business refusal reaches the caller as {@code INTERNAL_ERROR} and
 * reaches the on-call as an ERROR with a stack trace. The service works, the
 * dashboard lies, and the client's error handling has a branch that never
 * fires.</p>
 */
public final class RefusalCoverage {

    private RefusalCoverage() {
    }

    /**
     * Asserts the translator maps every {@code RuntimeException} declared under
     * {@code basePackage}, and that each mapping names a registry row.
     *
     * @param exempt classes that are deliberately defects rather than refusals
     */
    public static void assertEveryExceptionIsMapped(RefusalTranslator translator,
                                                    String basePackage,
                                                    Class<?>... exempt) {
        List<Class<?>> exemptions = List.of(exempt);
        List<Class<?>> declared = declaredExceptions(basePackage);

        assertThat(declared)
                .as("no exception classes found under %s — the scan is not looking where "
                        + "the exceptions live, so it would pass no matter what", basePackage)
                .isNotEmpty();

        Map<Class<?>, ErrorCode> mapped = new LinkedHashMap<>();
        List<String> unmapped = new ArrayList<>();

        for (Class<?> type : declared) {
            if (exemptions.contains(type)) {
                continue;
            }
            Optional<Throwable> instance = instantiate(type);
            if (instance.isEmpty()) {
                // Not a failure: a class with no reachable constructor cannot be
                // thrown by the shapes below either. Recorded so the count in
                // the message stays honest.
                continue;
            }
            Optional<DomainRefusal> refusal = translator.translate(instance.get());
            if (refusal.isPresent()) {
                mapped.put(type, refusal.get().errorCode());
            } else {
                unmapped.add(type.getSimpleName());
            }
        }

        assertThat(unmapped)
                .as("""
                    these exception classes have no registry code, so every one of them \
                    reaches the client as INTERNAL_ERROR and the log as an ERROR with a \
                    stack trace — for what the service considers an ordinary refusal. \
                    Add a row to the translator, or pass the class as exempt if it really \
                    is a defect.""")
                .isEmpty();

        assertThat(mapped)
                .as("the translator produced a mapping for at least one exception")
                .isNotEmpty();
    }

    /**
     * Asserts no service class competes with the platform error path.
     *
     * <h2>The failure this prevents is invisible</h2>
     * DGS collects every {@code DataFetcherExceptionResolver} bean and consults
     * them before the {@code DataFetcherExceptionHandler}, stopping at the first
     * non-null answer. Such a resolver almost always ends in a catch-all —
     * that shape reads as defensive completeness — so it answers every
     * exception and the platform handler never runs.
     *
     * <p>Nothing looks broken afterwards. Errors are still returned, queries
     * still fail correctly, and every test that asserts "a bad request produces
     * an error" still passes. What silently disappears is the whole contract:
     * registry codes, {@code retryable}, and the guarantee that no exception
     * message crosses the boundary.</p>
     *
     * <p>A second {@code DataFetcherExceptionHandler} fails differently and more
     * kindly — the context will not start, because DGS injects it by type. It is
     * checked here too so the reason is stated at the point of the mistake
     * rather than inferred from a {@code NoUniqueBeanDefinitionException}.</p>
     */
    public static void assertNoCompetingErrorBeans(String basePackage) {
        List<String> competing = new ArrayList<>();

        for (Class<?> competitor : List.of(
                graphql.execution.DataFetcherExceptionHandler.class,
                org.springframework.graphql.execution.DataFetcherExceptionResolver.class)) {

            ClassPathScanningCandidateComponentProvider scanner =
                    new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(competitor));

            for (BeanDefinition definition : scanner.findCandidateComponents(basePackage)) {
                competing.add(definition.getBeanClassName()
                        + " implements " + competitor.getSimpleName());
            }
        }

        assertThat(competing)
                .as("""
                    these shadow the platform error contract. A DataFetcherExceptionResolver \
                    is consulted before the platform handler and ends the chain as soon as it \
                    answers, so registry codes, retryable and the message-boundary guarantee \
                    all vanish while every error still appears to work. Raise a DomainRefusal \
                    or add a row to this service's RefusalTranslator instead.""")
                .isEmpty();
    }

    /**
     * One instance of every exception declared under a package that can be
     * built. Shared with {@link RestGraphQlParity} so both checks range over the
     * same set — a parity test covering fewer classes than the coverage test
     * would agree about exactly the exceptions nobody worried about.
     */
    public static List<Throwable> declaredExceptionInstances(String basePackage) {
        List<Throwable> instances = new ArrayList<>();
        for (Class<?> type : declaredExceptions(basePackage)) {
            instantiate(type).ifPresent(instances::add);
        }
        return instances;
    }

    /** Concrete {@code RuntimeException} subclasses declared under a package. */
    private static List<Class<?>> declaredExceptions(String basePackage) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(
                            org.springframework.beans.factory.annotation.AnnotatedBeanDefinition bd) {
                        return bd.getMetadata().isIndependent() && !bd.getMetadata().isAbstract();
                    }
                };
        scanner.addIncludeFilter(new AssignableTypeFilter(RuntimeException.class));

        List<Class<?>> found = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(basePackage)) {
            try {
                Class<?> type = Class.forName(definition.getBeanClassName());
                if (!Modifier.isAbstract(type.getModifiers())) {
                    found.add(type);
                }
            } catch (ClassNotFoundException unreachable) {
                throw new IllegalStateException(
                        "scanned class could not be loaded: " + definition.getBeanClassName(),
                        unreachable);
            }
        }
        return found;
    }

    /**
     * Builds an instance using whichever constructor takes only strings.
     *
     * <p>The translator dispatches on type, so the argument values are
     * irrelevant — but they must be <em>something</em>, and reflection is the
     * only way to reach constructors the test does not know the shape of.</p>
     */
    private static Optional<Throwable> instantiate(Class<?> type) {
        for (Constructor<?> constructor : type.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            boolean allStrings = parameters.length > 0;
            for (Class<?> parameter : parameters) {
                allStrings &= parameter == String.class;
            }
            if (!allStrings) {
                continue;
            }
            Object[] arguments = new Object[parameters.length];
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = "test";
            }
            try {
                return Optional.of((Throwable) constructor.newInstance(arguments));
            } catch (ReflectiveOperationException | ClassCastException notUsable) {
                // Try the next constructor.
            }
        }
        return Optional.empty();
    }
}
