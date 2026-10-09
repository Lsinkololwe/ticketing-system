package com.pml.shared.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Runs {@link LocalDatastoreGuard} before anything can open a connection.
 *
 * <h2>Why a {@code BeanFactoryPostProcessor} and not an ordinary bean</h2>
 * A {@code BeanFactoryPostProcessor} runs before <b>any</b> singleton is instantiated, so it
 * fires ahead of the Mongo client, ahead of every repository, and well ahead of the migration
 * runner on {@code ApplicationReadyEvent}. An ordinary {@code InitializingBean} would run
 * whenever its turn came, which is not ordered against the datastore client — and a check that
 * might run after the first connection is not a check.
 *
 * <p>The bean method is {@code static} so that reaching it does not force this configuration
 * class to be instantiated early, which is the documented requirement for contributing a
 * {@code BeanFactoryPostProcessor} from a configuration class.</p>
 *
 * <h2>No condition on the profile here</h2>
 * The profile test lives in the guard rather than in a {@code @ConditionalOnExpression}, so the
 * decision is one testable method rather than a condition string that only reveals itself at
 * runtime. It costs one no-op call on every other profile.
 */
@AutoConfiguration
public class LocalDatastoreGuardAutoConfiguration {

    /** {@code spring.data.mongodb.uri}, resolved from every property source including the env. */
    static final String MONGO_URI_PROPERTY = "spring.data.mongodb.uri";

    @Bean
    static BeanFactoryPostProcessor localDatastoreGuard(Environment environment) {
        return beanFactory -> LocalDatastoreGuard.check(
                environment.getProperty(MONGO_URI_PROPERTY),
                List.of(environment.getActiveProfiles()));
    }
}
