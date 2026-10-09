package com.pml.shared.config;

import com.pml.shared.persistence.MoneyFieldMigrationService;
import com.pml.shared.referencedata.StatusSemanticResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import java.time.Clock;

/**
 * Contributes the two shared helpers that services genuinely consume, without either of them
 * being a component-scanned {@code @Service}.
 *
 * <h2>Why these are beans rather than stereotypes</h2>
 * A library that declares {@code @Service} only works because every application happens to
 * component-scan the library's package — which the three services do,
 * {@code @ComponentScan(basePackages = {"com.pml.identity", "com.pml.shared"})}. That is an
 * implicit contract: scanning someone else's package means an application silently acquires
 * whatever that package starts declaring, and loses it just as silently if the scan is ever
 * narrowed.
 *
 * <p>Spring Boot's native answer is an {@link AutoConfiguration} that offers the bean
 * explicitly and defers to the application. {@code shared-library} already publishes several,
 * so these two join them instead of relying on the scan.
 *
 * <h2>These two still do not belong here</h2>
 * What belongs in a shared library is a short list — the JWT and role converters,
 * {@code DomainRefusal}, the error-code enum, {@code graphql/auth.graphqls}, and pure
 * utilities. Neither of these is on it:
 *
 * <ul>
 *   <li>{@link MoneyFieldMigrationService} is booking's money migration and has exactly one
 *       consumer, booking's own {@code DataMigrationRunner}. It belongs in booking.</li>
 *   <li>{@link StatusSemanticResolver} resolves a status code to its {@code WorkflowSemantic}
 *       and is consumed by identity and catalog. It belongs to the reference
 *       data engine.</li>
 * </ul>
 *
 * <p>Relocating them means rewriting consumers in three services. Until that happens this class
 * keeps them out of component scanning, and records the debt where the next reader will find
 * it.
 */
@AutoConfiguration
@ConditionalOnClass(ReactiveMongoTemplate.class)
public class SharedPersistenceSupportAutoConfiguration {

    /**
     * {@code ObjectProvider} rather than the template itself: both helpers are usable in a
     * context that has not configured MongoDB, and resolve lazily on first use.
     */
    @Bean
    @ConditionalOnMissingBean
    public MoneyFieldMigrationService moneyFieldMigrationService(
            ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider) {
        return new MoneyFieldMigrationService(mongoTemplateProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public StatusSemanticResolver statusSemanticResolver(
            ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider, Clock clock) {
        return new StatusSemanticResolver(mongoTemplateProvider, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public PlatformConfigurationReader platformConfigurationReader(
            ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider) {
        return new PlatformConfigurationReader(mongoTemplateProvider);
    }
}
