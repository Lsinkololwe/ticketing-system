package com.pml.shared.error;

import graphql.execution.DataFetcherExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Installs the platform error contract as the GraphQL error path.
 *
 * <h2>Why this replaces rather than joins</h2>
 * DGS's autoconfiguration declares {@code dataFetcherExceptionHandler()}
 * {@code @ConditionalOnMissingBean} and injects it into {@code graphQlSource}
 * <em>by type</em>. Exactly one such bean may exist in a context — a second one
 * fails startup with {@code NoUniqueBeanDefinitionException} rather than
 * layering. Defining it here is therefore the way to own the error path, and
 * the reason a service must not also define one.
 *
 * <p>The same method also collects every {@code DataFetcherExceptionResolver}
 * bean and consults them <b>before</b> this handler, stopping at the first
 * non-null answer. A resolver ending in a catch-all — the usual shape, since it
 * looks like defensive completeness — therefore answers every exception and
 * this handler never runs. The registry codes, {@code retryable} and the leak
 * guarantees would all be silently absent from a service that has one, so no
 * service may define a {@code DataFetcherExceptionResolver} either.
 * {@code ErrorContractWiringTest} asserts both, per service, because nothing
 * about the resulting behaviour looks broken from the outside: errors are still
 * returned, just in a different shape than the contract promises.</p>
 *
 * <h2>Translator order</h2>
 * {@code ObjectProvider.orderedStream()} honours {@code @Order}, and
 * {@link PlatformRefusalTranslator} is {@code LOWEST_PRECEDENCE}, so a service
 * translator always gets first refusal on an exception both understand.
 */
/*
 * `beforeName` rather than `before`: DGS is a provided-scope dependency, so the
 * class may be absent at runtime and a hard reference would fail to load. The
 * ordering itself is not optional — both this bean and DGS's default are
 * @ConditionalOnMissingBean, so whichever autoconfiguration is evaluated first
 * wins. Registered after DGS, this one backs off and the platform silently runs
 * on DGS's default handler with no registry codes and no leak guarantees.
 */
@AutoConfiguration(beforeName =
        "com.netflix.graphql.dgs.springgraphql.autoconfig.DgsSpringGraphQLAutoConfiguration")
@ConditionalOnClass(DataFetcherExceptionHandler.class)
public class ErrorContractAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PlatformRefusalTranslator platformRefusalTranslator() {
        return new PlatformRefusalTranslator();
    }

    @Bean
    @ConditionalOnMissingBean
    public DataFetcherExceptionHandler platformDataFetcherExceptionHandler(
            ObjectProvider<RefusalTranslator> translators) {
        List<RefusalTranslator> ordered = translators.orderedStream().toList();
        return new PlatformDataFetcherExceptionHandler(ordered);
    }

    /**
     * The REST half of the same contract.
     *
     * <p>Given the identical translator list, so a code cannot differ between
     * the two transports without someone registering a translator for one and
     * not the other.</p>
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestControllerAdvice")
    @ConditionalOnMissingBean
    public PlatformProblemDetailAdvice platformProblemDetailAdvice(
            ObjectProvider<RefusalTranslator> translators) {
        return new PlatformProblemDetailAdvice(translators.orderedStream().toList());
    }
}
